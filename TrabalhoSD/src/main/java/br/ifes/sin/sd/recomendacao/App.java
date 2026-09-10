package br.ifes.sin.sd.recomendacao;

import br.ifes.sin.sd.recomendacao.cliente.ClienteRecomendacao;
import br.ifes.sin.sd.recomendacao.servidor.ServidorRecomendacao;

/**
 * Ponto de entrada único da aplicação. O primeiro argumento escolhe o modo:
 *
 * <pre>
 *   java ... App servidor [porta]
 *   java ... App cliente  [host] [porta]
 *   java ... App teste
 * </pre>
 */
public final class App {

    public static void main(String[] args) throws Exception {
        String modo = args.length > 0 ? args[0].toLowerCase() : "";

        switch (modo) {
            case "servidor" -> {
                int porta = args.length > 1
                        ? Integer.parseInt(args[1])
                        : ServidorRecomendacao.PORTA_PADRAO;
                new ServidorRecomendacao(porta).iniciar();
            }
            case "cliente" -> {
                String host = args.length > 1 ? args[1] : "localhost";
                int porta = args.length > 2
                        ? Integer.parseInt(args[2])
                        : ServidorRecomendacao.PORTA_PADRAO;
                new ClienteRecomendacao(host, porta).executar();
            }
            case "teste" -> {
                boolean ok = Testes.executar();
                System.exit(ok ? 0 : 1);
            }
            default -> {
                System.out.println("Uso:");
                System.out.println("  App servidor [porta]         (padrao: " + ServidorRecomendacao.PORTA_PADRAO + ")");
                System.out.println("  App cliente  [host] [porta]  (padrao: localhost " + ServidorRecomendacao.PORTA_PADRAO + ")");
                System.out.println("  App teste                    (executa os testes automaticos)");
            }
        }
    }
}
