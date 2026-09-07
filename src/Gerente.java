import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Processo GERENTE do Contract Net Protocol.
 *
 * Responsabilidades:
 *  - manter a lista fixa de agentes conhecidos (IP + porta);
 *  - gerar uma sequencia de tarefas;
 *  - para cada tarefa executar as 4 fases do CNP:
 *      Fase 1  envia CFP a todos os agentes (UDP);
 *      Fase 2  coleta PROPOSTAs ate um timeout (setSoTimeout);
 *      Fase 3  envia ADJUDICACAO ao vencedor e REJEICAO aos demais;
 *      Fase 4  aguarda o RESULTADO do vencedor e o exibe no console.
 *
 * CRITERIO DE SELECAO DA MELHOR PROPOSTA:
 *  - vence a proposta de MENOR custo estimado;
 *  - em caso de EMPATE, vence o agente de MENOR numero de porta
 *    (criterio deterministico e facil de auditar nos logs).
 *
 * Uso:
 *   java Gerente [porta1 porta2 porta3 ...]
 *   - se nenhuma porta for informada, usa 9001 9002 9003 (localhost).
 */
public class Gerente {

    /** Endereco de um agente conhecido. */
    private static class EnderecoAgente {
        final InetAddress ip;
        final int porta;
        EnderecoAgente(InetAddress ip, int porta) { this.ip = ip; this.porta = porta; }
    }

    /** Proposta recebida de um agente. */
    private static class PropostaRecebida {
        final int porta;
        final long custo;
        PropostaRecebida(int porta, long custo) { this.porta = porta; this.custo = custo; }
    }

    private final List<EnderecoAgente> agentes = new ArrayList<>();
    private DatagramSocket socket;

    /* ============================================================= *
     *  Configuracao                                                 *
     * ============================================================= */

    private void configurarAgentes(int[] portas) throws IOException {
        InetAddress local = InetAddress.getByName("localhost");
        for (int p : portas) {
            agentes.add(new EnderecoAgente(local, p));
        }
        log("Agentes conhecidos: " + portasComoTexto());
    }

    /** Gera a sequencia fixa de tarefas a distribuir (>= 5 tarefas). */
    private List<Tarefa> gerarTarefas() {
        List<Tarefa> tarefas = new ArrayList<>();
        tarefas.add(new Tarefa(1, Tarefa.Tipo.FATORIAL,    12));
        tarefas.add(new Tarefa(2, Tarefa.Tipo.SOMA_PRIMOS, 2000));
        tarefas.add(new Tarefa(3, Tarefa.Tipo.FATORIAL,    18));
        tarefas.add(new Tarefa(4, Tarefa.Tipo.SOMA_PRIMOS, 5000));
        tarefas.add(new Tarefa(5, Tarefa.Tipo.FATORIAL,    25));
        tarefas.add(new Tarefa(6, Tarefa.Tipo.SOMA_PRIMOS, 8000));
        tarefas.add(new Tarefa(7, Tarefa.Tipo.FATORIAL,    30));
        return tarefas;
    }

    /* ============================================================= *
     *  Ciclo principal                                              *
     * ============================================================= */

    public void executar(int[] portas) {
        try {
            socket = new DatagramSocket();          // porta efemera para o Gerente
            configurarAgentes(portas);

            List<Tarefa> tarefas = gerarTarefas();
            log("Total de tarefas a distribuir: " + tarefas.size());
            log("=================================================");

            for (Tarefa tarefa : tarefas) {
                processarTarefa(tarefa);
                log("=================================================");
            }
            log("Todas as tarefas foram processadas. Encerrando o Gerente.");
        } catch (IOException e) {
            log("ERRO fatal de rede: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    /** Executa as 4 fases do CNP para uma unica tarefa. */
    private void processarTarefa(Tarefa tarefa) throws IOException {
        log("Nova rodada -> " + tarefa);

        // ---------- Fase 1: CFP ----------
        String cfp = Protocolo.montar(
                Protocolo.CFP, tarefa.getId(), tarefa.getTipo(), tarefa.getCarga());
        for (EnderecoAgente a : agentes) {
            socket.send(Protocolo.pacote(cfp, a.ip, a.porta));
        }
        log("Fase 1 -> CFP enviado a " + agentes.size() + " agente(s): " + cfp);

        // ---------- Fase 2: coleta de PROPOSTAs com timeout ----------
        List<PropostaRecebida> propostas = coletarPropostas(tarefa.getId());

        if (propostas.isEmpty()) {
            log("Fase 2 -> nenhuma proposta recebida dentro de "
                    + Protocolo.TIMEOUT_PROPOSTAS + "ms. Tarefa " + tarefa.getId()
                    + " sera descartada nesta rodada.");
            return;
        }

        // ---------- Fase 3: escolha do vencedor + ADJUDICACAO / REJEICAO ----------
        PropostaRecebida vencedora = escolherMelhor(propostas);
        log("Fase 3 -> vencedor: agente " + vencedora.porta + " com custo " + vencedora.custo);

        for (PropostaRecebida p : propostas) {
            if (p.porta == vencedora.porta) {
                // A ADJUDICACAO leva tipo e carga para o agente reconstruir a tarefa.
                String adj = Protocolo.montar(Protocolo.ADJUDICACAO, tarefa.getId(),
                        tarefa.getTipo(), tarefa.getCarga(), vencedora.porta);
                socket.send(Protocolo.pacote(adj, localhost(), p.porta));
                log("Fase 3 -> ADJUDICACAO enviada ao agente " + p.porta);
            } else {
                String rej = Protocolo.montar(Protocolo.REJEICAO, tarefa.getId(), p.porta);
                socket.send(Protocolo.pacote(rej, localhost(), p.porta));
                log("Fase 3 -> REJEICAO enviada ao agente " + p.porta);
            }
        }

        // ---------- Fase 4: aguarda o RESULTADO do vencedor ----------
        aguardarResultado(tarefa, vencedora.porta);
    }

    /* ============================================================= *
     *  Fase 2 - coleta de propostas                                 *
     * ============================================================= */
    private List<PropostaRecebida> coletarPropostas(int idTarefa) {
        List<PropostaRecebida> propostas = new ArrayList<>();
        byte[] buffer = new byte[Protocolo.BUFFER];
        long fim = System.currentTimeMillis() + Protocolo.TIMEOUT_PROPOSTAS;

        try {
            while (true) {
                long restante = fim - System.currentTimeMillis();
                if (restante <= 0) break;                 // janela de propostas encerrada

                socket.setSoTimeout((int) restante);      // nao trava indefinidamente
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                String[] campos = Protocolo.separar(Protocolo.texto(pacote));
                if (!Protocolo.PROPOSTA.equals(campos[0])) continue;
                if (Integer.parseInt(campos[1]) != idTarefa) continue; // proposta atrasada

                int portaAgente = Integer.parseInt(campos[2]);
                long custo = Long.parseLong(campos[3]);
                propostas.add(new PropostaRecebida(portaAgente, custo));
                log("Fase 2 <- PROPOSTA do agente " + portaAgente + " custo=" + custo);
            }
        } catch (SocketTimeoutException e) {
            // Esperado: expirou o tempo de espera por propostas.
            log("Fase 2 -> timeout de propostas atingido.");
        } catch (IOException e) {
            log("Fase 2 -> ERRO de rede ao coletar propostas: " + e.getMessage());
        }
        return propostas;
    }

    /* ============================================================= *
     *  Fase 3 - criterio de desempate                               *
     * ============================================================= */
    private PropostaRecebida escolherMelhor(List<PropostaRecebida> propostas) {
        PropostaRecebida melhor = propostas.get(0);
        for (PropostaRecebida p : propostas) {
            boolean menorCusto = p.custo < melhor.custo;
            boolean empateMenorPorta = (p.custo == melhor.custo) && (p.porta < melhor.porta);
            if (menorCusto || empateMenorPorta) {
                melhor = p;
            }
        }
        return melhor;
    }

    /* ============================================================= *
     *  Fase 4 - espera pelo resultado do vencedor                   *
     * ============================================================= */
    private void aguardarResultado(Tarefa tarefa, int portaVencedor) {
        byte[] buffer = new byte[Protocolo.BUFFER];
        try {
            socket.setSoTimeout(Protocolo.TIMEOUT_RESULTADO);
            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                String[] campos = Protocolo.separar(Protocolo.texto(pacote));
                if (!Protocolo.RESULTADO.equals(campos[0])) continue;
                if (Integer.parseInt(campos[1]) != tarefa.getId()) continue;

                int portaAgente = Integer.parseInt(campos[2]);
                String valor = campos[3];
                String tempoMs = campos[4];

                log("Fase 4 <- RESULTADO da tarefa " + tarefa.getId()
                        + " pelo agente " + portaAgente + " em " + tempoMs + "ms");
                log("Fase 4 -> valor calculado: " + resumir(valor));
                return;
            }
        } catch (SocketTimeoutException e) {
            log("Fase 4 -> o agente " + portaVencedor
                    + " nao entregou o RESULTADO da tarefa " + tarefa.getId()
                    + " dentro de " + Protocolo.TIMEOUT_RESULTADO + "ms.");
        } catch (IOException e) {
            log("Fase 4 -> ERRO de rede ao aguardar resultado: " + e.getMessage());
        }
    }

    /* ============================================================= *
     *  Utilitarios                                                  *
     * ============================================================= */
    private InetAddress localhost() throws IOException {
        return InetAddress.getByName("localhost");
    }

    private String portasComoTexto() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < agentes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(agentes.get(i).porta);
        }
        return sb.toString();
    }

    /** Evita poluir o console com numeros gigantes (fatorial de N grande). */
    private String resumir(String valor) {
        if (valor.length() <= 60) return valor;
        return valor.substring(0, 30) + "..." + valor.substring(valor.length() - 20)
                + " (" + valor.length() + " digitos)";
    }

    private void log(String msg) {
        System.out.println("[GERENTE] " + msg);
    }

    /* ============================================================= *
     *  main                                                         *
     * ============================================================= */
    public static void main(String[] args) {
        int[] portas;
        if (args.length >= 1) {
            portas = new int[args.length];
            for (int i = 0; i < args.length; i++) portas[i] = Integer.parseInt(args[i]);
        } else {
            portas = new int[] { 9001, 9002, 9003 };   // padrao: 3 agentes em localhost
        }
        new Gerente().executar(portas);
    }
}
