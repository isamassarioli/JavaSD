package br.ifes.sin.sd.recomendacao.servidor;

import br.ifes.sin.sd.recomendacao.modelo.Artista;
import br.ifes.sin.sd.recomendacao.modelo.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * Carga inicial exigida pelo enunciado: 15 artistas/bandas e 10 usuários, cada
 * usuário com uma avaliação (0 a 4) para cada artista.
 *
 * <p>Os perfis foram montados formando três grupos de afinidade — rock clássico
 * internacional, rock nacional e pop — para que as recomendações produzam
 * resultados perceptíveis durante a apresentação. Vários zeros ("não conheço")
 * foram deixados de propósito para exercitar a regra de tratamento do zero.</p>
 */
public final class DadosIniciais {

    private DadosIniciais() {
    }

    public static List<Artista> artistas() {
        String[] nomes = {
                "The Beatles",       // 1
                "Queen",             // 2
                "Pink Floyd",        // 3
                "Led Zeppelin",      // 4
                "Nirvana",           // 5
                "Metallica",         // 6
                "Iron Maiden",       // 7
                "U2",                // 8
                "Coldplay",          // 9
                "Imagine Dragons",   // 10
                "Legião Urbana",     // 11
                "Titãs",             // 12
                "Charlie Brown Jr.", // 13
                "Skank",             // 14
                "Capital Inicial"    // 15
        };
        List<Artista> lista = new ArrayList<>();
        for (int i = 0; i < nomes.length; i++) {
            lista.add(new Artista(i + 1, nomes[i]));
        }
        return lista;
    }

    public static List<Usuario> usuarios() {
        List<Usuario> lista = new ArrayList<>();
        //                                B  Q  PF LZ Ni Me IM U2 Co ID LU Ti CB Sk CI
        lista.add(new Usuario("Ana",     v(4, 3, 4, 3, 0, 1, 0, 3, 4, 4, 2, 0, 3, 2, 3)));
        lista.add(new Usuario("João",    v(4, 2, 3, 4, 1, 2, 0, 3, 3, 0, 2, 1, 0, 3, 2)));
        lista.add(new Usuario("Maria",   v(3, 4, 2, 0, 0, 0, 0, 4, 4, 4, 3, 2, 2, 3, 3)));
        lista.add(new Usuario("Pedro",   v(4, 4, 4, 4, 2, 3, 2, 2, 1, 0, 0, 0, 0, 1, 1)));
        lista.add(new Usuario("Beatriz", v(2, 2, 1, 1, 0, 0, 0, 2, 3, 3, 4, 4, 4, 4, 4)));
        lista.add(new Usuario("Lucas",   v(1, 2, 0, 1, 3, 4, 3, 2, 2, 1, 3, 4, 4, 3, 4)));
        lista.add(new Usuario("Carla",   v(4, 4, 3, 3, 1, 1, 0, 4, 4, 3, 2, 1, 2, 2, 3)));
        lista.add(new Usuario("Rafael",  v(2, 3, 1, 0, 0, 0, 0, 4, 4, 4, 2, 1, 3, 3, 2)));
        lista.add(new Usuario("Juliana", v(0, 3, 2, 2, 2, 2, 1, 3, 3, 2, 4, 4, 3, 4, 4)));
        lista.add(new Usuario("Bruno",   v(1, 1, 0, 2, 4, 4, 4, 1, 0, 0, 3, 3, 4, 2, 3)));
        return lista;
    }

    private static int[] v(int... notas) {
        return notas;
    }
}
