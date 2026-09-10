package br.ifes.sin.sd.recomendacao.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor central da aplicação distribuída.
 *
 * <ul>
 *   <li>escuta em uma porta TCP e aceita conexões de vários clientes;</li>
 *   <li>é <b>concorrente</b>: cada cliente é atendido por uma thread própria,
 *       obtida de um pool ({@link Executors#newCachedThreadPool()});</li>
 *   <li>mantém os dados dos usuários e suas avaliações em {@link BaseDeDados},
 *       compartilhada entre todas as threads.</li>
 * </ul>
 */
public final class ServidorRecomendacao {

    public static final int PORTA_PADRAO = 5000;

    private final int porta;
    private final BaseDeDados base;
    private final MotorDeRecomendacao motor;
    private final ExecutorService poolDeThreads = Executors.newCachedThreadPool();
    private final AtomicInteger contadorDeConexoes = new AtomicInteger();

    public ServidorRecomendacao(int porta) {
        this.porta = porta;
        this.base = new BaseDeDados(DadosIniciais.artistas(), DadosIniciais.usuarios());
        this.motor = new MotorDeRecomendacao(base);
    }

    public void iniciar() throws IOException {
        try (ServerSocket servidor = new ServerSocket(porta)) {
            registrar("Servidor de recomendação musical no ar na porta " + porta);
            registrar(base.artistas().size() + " artistas e " + base.usuarios().size()
                    + " usuários carregados. Aguardando clientes...");

            while (true) {
                Socket cliente = servidor.accept();
                int numero = contadorDeConexoes.incrementAndGet();
                registrar("Conexão #" + numero + " aceita de " + cliente.getRemoteSocketAddress());
                poolDeThreads.submit(new AtendenteCliente(numero, cliente, base, motor));
            }
        }
    }

    /** Log simples com horário, útil para demonstrar a concorrência na apresentação. */
    static void registrar(String mensagem) {
        System.out.println("[" + LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + "] "
                + "[" + Thread.currentThread().getName() + "] " + mensagem);
    }
}
