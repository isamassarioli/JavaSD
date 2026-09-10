package br.ifes.sin.sd.recomendacao.modelo;

import java.util.Arrays;

/**
 * Usuário do sistema, representado pelo seu <b>perfil musical</b>: um vetor de
 * 15 posições em que cada posição guarda a nota atribuída ao artista de código
 * correspondente (posição 0 -&gt; artista 1, ..., posição 14 -&gt; artista 15).
 *
 * <p>Notas válidas:</p>
 * <pre>
 *   0 - Não conheço ou ainda não avaliei
 *   1 - Não gosto
 *   2 - Gosto muito pouco
 *   3 - Gosto
 *   4 - Gosto muito
 * </pre>
 *
 * <p>Os métodos que leem ou alteram o vetor são {@code synchronized}: como o
 * servidor é concorrente, a mesma instância de {@code Usuario} pode ser acessada
 * por várias threads (por exemplo, um cliente registrando uma avaliação enquanto
 * outro pede recomendações).</p>
 */
public final class Usuario {

    /** Quantidade fixa de artistas/bandas do sistema. */
    public static final int TOTAL_ARTISTAS = 15;

    private final String nome;
    private final int[] avaliacoes;

    public Usuario(String nome, int[] avaliacoes) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do usuário é obrigatório");
        }
        if (avaliacoes == null || avaliacoes.length != TOTAL_ARTISTAS) {
            throw new IllegalArgumentException(
                    "O perfil musical deve ter exatamente " + TOTAL_ARTISTAS + " avaliações");
        }
        for (int nota : avaliacoes) {
            validarNota(nota);
        }
        this.nome = nome.trim();
        this.avaliacoes = avaliacoes.clone();
    }

    public String nome() {
        return nome;
    }

    /** Nota que o usuário deu ao artista guardado na posição informada (0 a 14). */
    public synchronized int nota(int indice) {
        return avaliacoes[indice];
    }

    /** Cópia defensiva do vetor de avaliações. */
    public synchronized int[] avaliacoes() {
        return avaliacoes.clone();
    }

    /** Registra (ou substitui) a avaliação de um artista. */
    public synchronized void avaliar(int indiceArtista, int nota) {
        if (indiceArtista < 0 || indiceArtista >= TOTAL_ARTISTAS) {
            throw new IllegalArgumentException("Posição de artista inválida: " + indiceArtista);
        }
        validarNota(nota);
        avaliacoes[indiceArtista] = nota;
    }

    public static void validarNota(int nota) {
        if (nota < 0 || nota > 4) {
            throw new IllegalArgumentException("Nota inválida: " + nota + " (use um valor de 0 a 4)");
        }
    }

    @Override
    public synchronized String toString() {
        return nome + " " + Arrays.toString(avaliacoes);
    }
}
