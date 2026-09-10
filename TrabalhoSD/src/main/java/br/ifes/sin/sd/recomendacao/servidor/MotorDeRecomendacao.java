package br.ifes.sin.sd.recomendacao.servidor;

import br.ifes.sin.sd.recomendacao.modelo.Artista;
import br.ifes.sin.sd.recomendacao.modelo.Usuario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Núcleo do processamento distribuído: calcula a semelhança entre perfis
 * musicais pela <b>distância euclidiana</b> e, a partir dos vizinhos mais
 * próximos, produz recomendações.
 *
 * <h2>Tratamento das notas iguais a zero</h2>
 * <p>A nota {@code 0} significa "não conheço ou ainda não avaliei" e <b>não</b>
 * deve ser tratada como uma preferência negativa. Por isso, no cálculo da
 * distância entre dois usuários só entram as posições em que <b>ambos</b>
 * possuem avaliação diferente de zero (regra recomendada no item 5 do
 * enunciado). Se não houver nenhum artista avaliado pelos dois, os perfis são
 * considerados incomparáveis e o resultado é {@link OptionalDouble#empty()}.</p>
 */
public final class MotorDeRecomendacao {

    /** Quantidade padrão de vizinhos considerados quando o cliente não informa. */
    public static final int VIZINHOS_PADRAO = 3;

    /** Quantidade padrão de recomendações devolvidas quando o cliente não informa. */
    public static final int RECOMENDACOES_PADRAO = 5;

    private final BaseDeDados base;

    public MotorDeRecomendacao(BaseDeDados base) {
        this.base = base;
    }

    /**
     * Distância euclidiana entre dois perfis, considerando apenas os artistas
     * avaliados (nota diferente de zero) por ambos os usuários.
     *
     * <pre>
     *   d(A, B) = raiz( soma( (A_i - B_i)^2 ) )   para todo i em que A_i != 0 e B_i != 0
     * </pre>
     */
    public static OptionalDouble distancia(Usuario a, Usuario b) {
        double somaQuadrados = 0.0;
        int artistasComparados = 0;
        for (int i = 0; i < Usuario.TOTAL_ARTISTAS; i++) {
            int notaA = a.nota(i);
            int notaB = b.nota(i);
            if (notaA != 0 && notaB != 0) {
                double diferenca = notaA - notaB;
                somaQuadrados += diferenca * diferenca;
                artistasComparados++;
            }
        }
        if (artistasComparados == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.sqrt(somaQuadrados));
    }

    /** Número de artistas avaliados (nota != 0) simultaneamente pelos dois usuários. */
    public static int artistasEmComum(Usuario a, Usuario b) {
        int comuns = 0;
        for (int i = 0; i < Usuario.TOTAL_ARTISTAS; i++) {
            if (a.nota(i) != 0 && b.nota(i) != 0) {
                comuns++;
            }
        }
        return comuns;
    }

    /**
     * Lista os usuários mais semelhantes ao alvo, em ordem crescente de
     * distância (quanto menor a distância, mais parecido é o gosto musical).
     *
     * @param quantidade número máximo de vizinhos; use um valor &lt;= 0 para todos
     */
    public List<Vizinho> vizinhosMaisSemelhantes(Usuario alvo, int quantidade) {
        List<Vizinho> vizinhos = new ArrayList<>();
        for (Usuario outro : base.usuarios()) {
            if (outro == alvo) {
                continue;
            }
            OptionalDouble distancia = distancia(alvo, outro);
            if (distancia.isPresent()) {
                vizinhos.add(new Vizinho(outro, distancia.getAsDouble(), artistasEmComum(alvo, outro)));
            }
        }
        vizinhos.sort(Comparator.comparingDouble(Vizinho::distancia)
                .thenComparing(vizinho -> vizinho.usuario().nome()));

        if (quantidade > 0 && vizinhos.size() > quantidade) {
            return new ArrayList<>(vizinhos.subList(0, quantidade));
        }
        return vizinhos;
    }

    /**
     * Gera recomendações para o usuário alvo.
     *
     * <p>Para cada artista que o alvo ainda não avaliou (nota 0), calcula-se a
     * média das notas dadas pelos vizinhos mais semelhantes, ponderada pela
     * proximidade de cada vizinho (peso = 1 / (1 + distância)). As recomendações
     * são ordenadas da maior para a menor nota prevista.</p>
     */
    public List<Recomendacao> recomendar(Usuario alvo, int quantidadeVizinhos, int maxRecomendacoes) {
        List<Vizinho> vizinhos = vizinhosMaisSemelhantes(alvo, quantidadeVizinhos);
        List<Recomendacao> recomendacoes = new ArrayList<>();

        for (int i = 0; i < Usuario.TOTAL_ARTISTAS; i++) {
            if (alvo.nota(i) != 0) {
                continue; // já conhece / já avaliou este artista
            }
            double numerador = 0.0;
            double denominador = 0.0;
            List<String> baseadoEm = new ArrayList<>();

            for (Vizinho vizinho : vizinhos) {
                int notaVizinho = vizinho.usuario().nota(i);
                if (notaVizinho == 0) {
                    continue; // o vizinho também não avaliou este artista
                }
                double peso = 1.0 / (1.0 + vizinho.distancia());
                numerador += peso * notaVizinho;
                denominador += peso;
                baseadoEm.add(vizinho.usuario().nome());
            }

            if (denominador > 0.0) {
                double notaPrevista = numerador / denominador;
                recomendacoes.add(new Recomendacao(base.artistaPorId(i + 1), notaPrevista, baseadoEm));
            }
        }

        recomendacoes.sort(Comparator.comparingDouble(Recomendacao::notaPrevista).reversed()
                .thenComparing(recomendacao -> recomendacao.artista().nome()));

        if (maxRecomendacoes > 0 && recomendacoes.size() > maxRecomendacoes) {
            return new ArrayList<>(recomendacoes.subList(0, maxRecomendacoes));
        }
        return recomendacoes;
    }

    /** Usuário semelhante ao alvo, com a distância calculada. */
    public record Vizinho(Usuario usuario, double distancia, int artistasEmComum) {
    }

    /** Artista sugerido ao alvo, com a nota prevista e os vizinhos que a embasaram. */
    public record Recomendacao(Artista artista, double notaPrevista, List<String> baseadoEm) {
    }
}
