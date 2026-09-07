import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processo AGENTE do Contract Net Protocol.
 *
 * Cada instancia e um agente autonomo que escuta em uma porta UDP propria
 * (ex.: 9001, 9002, 9003 ...). O agente mantem uma "carga de trabalho atual"
 * simulada e participa das 4 fases do CNP:
 *
 *   Fase 1  recebe CFP         -> calcula custo e responde PROPOSTA
 *   Fase 3  recebe ADJUDICACAO -> executa a tarefa e responde RESULTADO
 *           recebe REJEICAO    -> apenas registra em log
 *   Fase 4  envia RESULTADO
 *
 * Apos executar uma tarefa o agente INCREMENTA sua carga simulada, ficando
 * "mais ocupado" nas proximas rodadas (permite observar o balanceamento).
 *
 * Uso:
 *   java Agente <porta> [cargaInicial]
 *   - porta        : porta UDP de escuta (obrigatorio)
 *   - cargaInicial : carga inicial (opcional; se ausente, sorteia de 1 a 10)
 */
public class Agente {

    private final int porta;
    private int cargaAtual;              // carga de trabalho simulada
    private DatagramSocket socket;

    public Agente(int porta, int cargaInicial) {
        this.porta = porta;
        this.cargaAtual = cargaInicial;
    }

    /* ============================================================= *
     *  Ciclo de vida                                                *
     * ============================================================= */

    /** Abre o socket UDP e entra no laco de atendimento. */
    public void iniciar() {
        try {
            socket = new DatagramSocket(porta);
            log("Agente no ar. Carga inicial = " + cargaAtual);
            lacoDeAtendimento();
        } catch (IOException e) {
            log("ERRO ao iniciar o socket UDP: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /** Recebe datagramas indefinidamente e despacha conforme o tipo. */
    private void lacoDeAtendimento() {
        byte[] buffer = new byte[Protocolo.BUFFER];
        while (true) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);                       // bloqueante
                String mensagem = Protocolo.texto(pacote);
                tratarMensagem(mensagem, pacote.getAddress(), pacote.getPort());
            } catch (IOException e) {
                log("ERRO de rede ao receber pacote: " + e.getMessage());
            }
        }
    }

    /** Encaminha a mensagem recebida para o tratador da fase correspondente. */
    private void tratarMensagem(String mensagem, InetAddress ipGerente, int portaGerente)
            throws IOException {
        String[] campos = Protocolo.separar(mensagem);
        String tipo = campos[0];

        switch (tipo) {
            case Protocolo.CFP:
                tratarCFP(campos, ipGerente, portaGerente);
                break;
            case Protocolo.ADJUDICACAO:
                tratarAdjudicacao(campos, ipGerente, portaGerente);
                break;
            case Protocolo.REJEICAO:
                log("Fase 3 <- REJEICAO na tarefa " + campos[1] + " (proposta nao escolhida)");
                break;
            default:
                log("Mensagem ignorada (tipo desconhecido): " + mensagem);
        }
    }

    /* ============================================================= *
     *  Fase 1/2 - CFP recebido  =>  responde PROPOSTA               *
     * ============================================================= */
    private void tratarCFP(String[] campos, InetAddress ipGerente, int portaGerente)
            throws IOException {
        int idTarefa = Integer.parseInt(campos[1]);
        String tipoTarefa = campos[2];
        int cargaTarefa = Integer.parseInt(campos[3]);

        log("Fase 1 <- CFP tarefa " + idTarefa + " (" + tipoTarefa + ", carga=" + cargaTarefa + ")");

        // Regra de custo: custo_estimado = carga_atual * fator_da_tarefa
        // (fator_da_tarefa = a carga/N da tarefa anunciada no CFP)
        long custo = (long) cargaAtual * cargaTarefa;

        String proposta = Protocolo.montar(Protocolo.PROPOSTA, idTarefa, porta, custo);
        socket.send(Protocolo.pacote(proposta, ipGerente, portaGerente));
        log("Fase 2 -> PROPOSTA tarefa " + idTarefa + " custo=" + custo
                + " (carga_atual=" + cargaAtual + ")");
    }

    /* ============================================================= *
     *  Fase 3/4 - ADJUDICACAO recebida  =>  executa e responde      *
     * ============================================================= */
    private void tratarAdjudicacao(String[] campos, InetAddress ipGerente, int portaGerente)
            throws IOException {
        int idTarefa = Integer.parseInt(campos[1]);
        log("Fase 3 <- ADJUDICACAO tarefa " + idTarefa + " (venci o leilao)");

        // Reconstroi a tarefa e executa o calculo de fato.
        // O tipo/carga foram guardados no CFP; para manter o agente sem estado
        // usamos os campos extras enviados na ADJUDICACAO.
        Tarefa.Tipo tipo = Tarefa.Tipo.valueOf(campos[2]);
        int cargaTarefa = Integer.parseInt(campos[3]);
        Tarefa tarefa = new Tarefa(idTarefa, tipo, cargaTarefa);

        long inicio = System.currentTimeMillis();
        String valor = tarefa.executar();
        long tempoMs = System.currentTimeMillis() - inicio;

        String resultado = Protocolo.montar(
                Protocolo.RESULTADO, idTarefa, porta, valor, tempoMs);
        socket.send(Protocolo.pacote(resultado, ipGerente, portaGerente));
        log("Fase 4 -> RESULTADO tarefa " + idTarefa + " tempo=" + tempoMs + "ms");

        // Fica "mais ocupado" para as proximas rodadas (balanceamento de carga).
        cargaAtual++;
        log("Carga simulada incrementada para " + cargaAtual);
    }

    /* ============================================================= *
     *  Log padronizado                                              *
     * ============================================================= */
    private void log(String msg) {
        System.out.println("[AGENTE " + porta + "] " + msg);
    }

    /* ============================================================= *
     *  main                                                         *
     * ============================================================= */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: java Agente <porta> [cargaInicial]");
            return;
        }
        int porta = Integer.parseInt(args[0]);
        int cargaInicial = (args.length >= 2)
                ? Integer.parseInt(args[1])
                : ThreadLocalRandom.current().nextInt(1, 11); // 1 a 10

        new Agente(porta, cargaInicial).iniciar();
    }
}
