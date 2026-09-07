import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Classe auxiliar com as constantes e utilitarios do protocolo textual
 * usado na comunicacao UDP entre o Gerente e os Agentes.
 *
 * ------------------------------------------------------------------
 * FORMATO DAS MENSAGENS (campos separados por ';')
 * ------------------------------------------------------------------
 *  1) CFP          -> CFP;<idTarefa>;<tipo>;<carga>
 *                     ex.: CFP;3;FATORIAL;10
 *  2) PROPOSTA     -> PROPOSTA;<idTarefa>;<idAgente>;<custoEstimado>
 *                     ex.: PROPOSTA;3;9001;42
 *  3) ADJUDICACAO  -> ADJUDICACAO;<idTarefa>;<idAgente>
 *                     ex.: ADJUDICACAO;3;9001
 *  4) REJEICAO     -> REJEICAO;<idTarefa>;<idAgente>
 *                     ex.: REJEICAO;3;9002
 *  5) RESULTADO    -> RESULTADO;<idTarefa>;<idAgente>;<valor>;<tempoMs>
 *                     ex.: RESULTADO;3;9001;3628800;5
 * ------------------------------------------------------------------
 * As 4 fases do Contract Net Protocol (CNP):
 *   Fase 1 - Anuncio da tarefa .......... CFP
 *   Fase 2 - Envio de propostas ......... PROPOSTA
 *   Fase 3 - Adjudicacao / Rejeicao ..... ADJUDICACAO / REJEICAO
 *   Fase 4 - Entrega do resultado ....... RESULTADO
 * ------------------------------------------------------------------
 */
public final class Protocolo {

    /** Separador de campos das mensagens. */
    public static final String SEP = ";";

    /* Identificadores das mensagens (primeiro campo). */
    public static final String CFP         = "CFP";
    public static final String PROPOSTA    = "PROPOSTA";
    public static final String ADJUDICACAO = "ADJUDICACAO";
    public static final String REJEICAO    = "REJEICAO";
    public static final String RESULTADO   = "RESULTADO";

    /** Tempo limite (ms) que o Gerente aguarda pelas propostas. */
    public static final int TIMEOUT_PROPOSTAS = 3000;

    /** Tempo limite (ms) que o Gerente aguarda pelo resultado do vencedor. */
    public static final int TIMEOUT_RESULTADO = 15000;

    /** Tamanho do buffer de recepcao dos datagramas. */
    public static final int BUFFER = 2048;

    private Protocolo() { }

    /* ============================================================= *
     *  Utilitarios de (de)serializacao                              *
     * ============================================================= */

    /** Monta uma mensagem juntando os campos com o separador padrao. */
    public static String montar(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(String.valueOf(campos[i]));
        }
        return sb.toString();
    }

    /** Divide a mensagem recebida em seus campos. */
    public static String[] separar(String mensagem) {
        return mensagem.split(SEP);
    }

    /** Extrai o texto (sem espacos nas pontas) de um DatagramPacket. */
    public static String texto(DatagramPacket p) {
        return new String(p.getData(), 0, p.getLength()).trim();
    }

    /** Cria um DatagramPacket pronto para envio ao destino informado. */
    public static DatagramPacket pacote(String mensagem, InetAddress ip, int porta) {
        byte[] dados = mensagem.getBytes();
        return new DatagramPacket(dados, dados.length, ip, porta);
    }
}
