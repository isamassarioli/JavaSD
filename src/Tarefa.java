import java.math.BigInteger;

/**
 * Representa uma tarefa computacional a ser distribuida pelo Gerente.
 *
 * Cada tarefa possui:
 *  - id      : identificador unico (inteiro sequencial);
 *  - tipo    : FATORIAL ou SOMA_PRIMOS;
 *  - carga   : "carga de trabalho" simulada (o N do calculo). Tambem e
 *              usada como "fator_da_tarefa" no calculo do custo dos agentes.
 *
 * A propria classe sabe executar o calculo (metodo {@link #executar()}),
 * garantindo que Gerente e Agente concordem sobre o resultado esperado.
 */
public class Tarefa {

    public enum Tipo { FATORIAL, SOMA_PRIMOS }

    private final int id;
    private final Tipo tipo;
    private final int carga;

    public Tarefa(int id, Tipo tipo, int carga) {
        this.id = id;
        this.tipo = tipo;
        this.carga = carga;
    }

    public int getId()      { return id; }
    public Tipo getTipo()   { return tipo; }
    public int getCarga()   { return carga; }

    /** Fator usado pelos agentes no calculo do custo da proposta. */
    public int getFator()   { return carga; }

    /**
     * Executa de fato o calculo da tarefa.
     * @return o resultado como String (pode ser um numero muito grande).
     */
    public String executar() {
        switch (tipo) {
            case FATORIAL:    return fatorial(carga).toString();
            case SOMA_PRIMOS: return String.valueOf(somaNPrimos(carga));
            default:          throw new IllegalStateException("Tipo desconhecido: " + tipo);
        }
    }

    /** Fatorial de n usando BigInteger (suporta N grande). */
    private static BigInteger fatorial(int n) {
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            r = r.multiply(BigInteger.valueOf(i));
        }
        return r;
    }

    /** Soma dos n primeiros numeros primos. */
    private static long somaNPrimos(int n) {
        long soma = 0;
        int encontrados = 0;
        int candidato = 1;
        while (encontrados < n) {
            candidato++;
            if (ehPrimo(candidato)) {
                soma += candidato;
                encontrados++;
            }
        }
        return soma;
    }

    private static boolean ehPrimo(int x) {
        if (x < 2) return false;
        for (int d = 2; (long) d * d <= x; d++) {
            if (x % d == 0) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Tarefa#" + id + " [" + tipo + ", carga=" + carga + "]";
    }
}
