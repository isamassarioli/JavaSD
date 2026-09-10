package br.ifes.sin.sd.recomendacao.modelo;

/** Artista ou banda musical conhecido pelo sistema. */
public final class Artista {

    private final int id;
    private final String nome;

    public Artista(int id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    public int id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    @Override
    public String toString() {
        return id + " - " + nome;
    }
}
