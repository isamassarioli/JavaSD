package br.ifes.sin.sd.recomendacao.servidor;

import br.ifes.sin.sd.recomendacao.modelo.Artista;
import br.ifes.sin.sd.recomendacao.modelo.Usuario;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório em memória, compartilhado por todas as conexões atendidas pelo
 * servidor. É a região crítica do sistema concorrente.
 *
 * <p>Estratégia de sincronização:</p>
 * <ul>
 *   <li>a lista de artistas é imutável após a criação ({@link List#copyOf});</li>
 *   <li>o mapa de usuários é um {@link ConcurrentHashMap}, o que torna seguras
 *       as operações de leitura, inserção e iteração feitas em paralelo;</li>
 *   <li>a alteração do vetor de um usuário é protegida dentro da própria classe
 *       {@link Usuario} (métodos {@code synchronized}).</li>
 * </ul>
 */
public final class BaseDeDados {

    private final List<Artista> artistas;
    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();

    public BaseDeDados(List<Artista> artistas, List<Usuario> usuariosIniciais) {
        this.artistas = List.copyOf(artistas);
        for (Usuario usuario : usuariosIniciais) {
            usuarios.put(chave(usuario.nome()), usuario);
        }
    }

    public List<Artista> artistas() {
        return artistas;
    }

    public Artista artistaPorId(int id) {
        if (id < 1 || id > artistas.size()) {
            throw new IllegalArgumentException("Artista inexistente: código " + id);
        }
        return artistas.get(id - 1);
    }

    public Collection<Usuario> usuarios() {
        return usuarios.values();
    }

    public Usuario buscarUsuario(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        return usuarios.get(chave(nome));
    }

    public Usuario exigirUsuario(String nome) {
        Usuario usuario = buscarUsuario(nome);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuário não encontrado: " + nome);
        }
        return usuario;
    }

    public Usuario cadastrarUsuario(String nome, int[] avaliacoes) {
        Usuario novo = new Usuario(nome, avaliacoes);
        Usuario anterior = usuarios.putIfAbsent(chave(novo.nome()), novo);
        if (anterior != null) {
            throw new IllegalStateException("Já existe um usuário chamado '" + novo.nome() + "'");
        }
        return novo;
    }

    private static String chave(String nome) {
        return nome.trim().toLowerCase(Locale.ROOT);
    }
}
