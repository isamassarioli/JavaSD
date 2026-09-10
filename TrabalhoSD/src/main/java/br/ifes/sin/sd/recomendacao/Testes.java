package br.ifes.sin.sd.recomendacao;

import br.ifes.sin.sd.recomendacao.modelo.Usuario;
import br.ifes.sin.sd.recomendacao.servidor.BaseDeDados;
import br.ifes.sin.sd.recomendacao.servidor.DadosIniciais;
import br.ifes.sin.sd.recomendacao.servidor.MotorDeRecomendacao;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Testes automáticos simples, sem dependência de framework. Executados por
 * {@code java ... App teste}. Cobrem a regra de tratamento do zero, os exemplos
 * numéricos do enunciado e o comportamento básico das recomendações.
 */
public final class Testes {

    private static int total;
    private static int falhas;

    private Testes() {
    }

    public static boolean executar() {
        total = 0;
        falhas = 0;

        distanciaUsaSomenteArtistasAvaliadosPelosDois();
        exemploDoEnunciadoSecao5();
        perfisSemArtistasEmComumSaoIncomparaveis();
        vizinhoMaisProximoTemMenorDistancia();
        recomendacaoNaoRepeteArtistaJaAvaliado();

        System.out.println();
        System.out.println("Testes executados: " + total + " | falhas: " + falhas);
        return falhas == 0;
    }

    // ------------------------------------------------------------------
    // Casos
    // ------------------------------------------------------------------

    private static void distanciaUsaSomenteArtistasAvaliadosPelosDois() {
        // Ana  = [4, 3, 0, 4, 2, ...]   Joao = [3, 2, 4, 4, 0, ...]
        // Comparados apenas: artista 1 (4x3), artista 2 (3x2) e artista 4 (4x4).
        // d = raiz( 1 + 1 + 0 ) = raiz(2)
        Usuario ana = usuario("Ana", 4, 3, 0, 4, 2);
        Usuario joao = usuario("Joao", 3, 2, 4, 4, 0);
        OptionalDouble d = MotorDeRecomendacao.distancia(ana, joao);

        verificar("distancia ignora posicoes com zero em algum dos usuarios",
                d.isPresent() && quaseIgual(d.getAsDouble(), Math.sqrt(2)));
        verificar("apenas 3 artistas sao considerados em comum",
                MotorDeRecomendacao.artistasEmComum(ana, joao) == 3);
    }

    private static void exemploDoEnunciadoSecao5() {
        // Mesmo exemplo da secao 5 do enunciado: resultado esperado = raiz(2) ~= 1,4142
        Usuario ana = usuario("Ana", 4, 3, 0, 4, 2);
        Usuario joao = usuario("Joao", 3, 2, 4, 4, 0);
        double d = MotorDeRecomendacao.distancia(ana, joao).orElse(-1);
        verificar("exemplo da secao 5 resulta em raiz(2)", quaseIgual(d, 1.4142135623730951));
    }

    private static void perfisSemArtistasEmComumSaoIncomparaveis() {
        Usuario a = usuario("A", 4, 4, 0, 0, 0);
        Usuario b = usuario("B", 0, 0, 3, 3, 3);
        verificar("perfis sem artistas avaliados em comum retornam Optional vazio",
                MotorDeRecomendacao.distancia(a, b).isEmpty());
    }

    private static void vizinhoMaisProximoTemMenorDistancia() {
        BaseDeDados base = new BaseDeDados(DadosIniciais.artistas(), DadosIniciais.usuarios());
        MotorDeRecomendacao motor = new MotorDeRecomendacao(base);

        List<MotorDeRecomendacao.Vizinho> vizinhos =
                motor.vizinhosMaisSemelhantes(base.exigirUsuario("Ana"), 0);

        boolean ordenado = true;
        for (int i = 1; i < vizinhos.size(); i++) {
            if (vizinhos.get(i - 1).distancia() > vizinhos.get(i).distancia()) {
                ordenado = false;
                break;
            }
        }
        verificar("vizinhos vem ordenados por distancia crescente", ordenado && !vizinhos.isEmpty());
    }

    private static void recomendacaoNaoRepeteArtistaJaAvaliado() {
        BaseDeDados base = new BaseDeDados(DadosIniciais.artistas(), DadosIniciais.usuarios());
        MotorDeRecomendacao motor = new MotorDeRecomendacao(base);
        Usuario ana = base.exigirUsuario("Ana");

        boolean somenteDesconhecidos = motor.recomendar(ana, 3, 10).stream()
                .allMatch(recomendacao -> ana.nota(recomendacao.artista().id() - 1) == 0);
        verificar("recomendacoes contem apenas artistas ainda nao avaliados pelo alvo", somenteDesconhecidos);
    }

    // ------------------------------------------------------------------
    // Infraestrutura de teste
    // ------------------------------------------------------------------

    private static Usuario usuario(String nome, int... primeirasNotas) {
        int[] avaliacoes = new int[Usuario.TOTAL_ARTISTAS];
        System.arraycopy(primeirasNotas, 0, avaliacoes, 0, primeirasNotas.length);
        return new Usuario(nome, avaliacoes);
    }

    private static boolean quaseIgual(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static void verificar(String descricao, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("  [OK]    " + descricao);
        } else {
            falhas++;
            System.out.println("  [FALHA] " + descricao);
        }
    }
}
