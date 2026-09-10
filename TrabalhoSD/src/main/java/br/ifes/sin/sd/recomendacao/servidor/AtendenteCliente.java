package br.ifes.sin.sd.recomendacao.servidor;

import br.ifes.sin.sd.recomendacao.json.Json;
import br.ifes.sin.sd.recomendacao.json.JsonException;
import br.ifes.sin.sd.recomendacao.modelo.Artista;
import br.ifes.sin.sd.recomendacao.modelo.Usuario;
import br.ifes.sin.sd.recomendacao.protocolo.Operacao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trata uma única conexão de cliente. Uma instância roda em sua própria thread.
 *
 * <p><b>Protocolo:</b> texto, uma mensagem JSON por linha (terminada por
 * {@code \n}), codificação UTF-8. O cliente envia uma requisição e o servidor
 * responde com exatamente uma linha JSON. A conexão permanece aberta para
 * várias requisições, até o cliente fechá-la.</p>
 *
 * <pre>
 *   Requisição: {"operacao":"RECOMENDAR","dados":{"usuario":"Ana","vizinhos":3}}
 *   Resposta OK:   {"ok":true,"dados":{ ... }}
 *   Resposta erro: {"ok":false,"erro":"mensagem"}
 * </pre>
 */
public final class AtendenteCliente implements Runnable {

    private final int numeroDaConexao;
    private final Socket socket;
    private final BaseDeDados base;
    private final MotorDeRecomendacao motor;

    public AtendenteCliente(int numeroDaConexao, Socket socket, BaseDeDados base, MotorDeRecomendacao motor) {
        this.numeroDaConexao = numeroDaConexao;
        this.socket = socket;
        this.base = base;
        this.motor = motor;
    }

    @Override
    public void run() {
        try (Socket conexao = this.socket;
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter saida = new BufferedWriter(
                     new OutputStreamWriter(conexao.getOutputStream(), StandardCharsets.UTF_8))) {

            String linha;
            while ((linha = entrada.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                String resposta = processar(linha);
                saida.write(resposta);
                saida.write("\n");
                saida.flush();
            }
        } catch (IOException e) {
            ServidorRecomendacao.registrar("Conexão #" + numeroDaConexao + " interrompida: " + e.getMessage());
        } finally {
            ServidorRecomendacao.registrar("Conexão #" + numeroDaConexao + " encerrada");
        }
    }

    private String processar(String linha) {
        try {
            Map<String, Object> requisicao = Json.comoObjeto(Json.ler(linha));
            Operacao operacao = Operacao.de(Json.texto(requisicao, "operacao"));
            Map<String, Object> dados = requisicao.get("dados") == null
                    ? Map.of()
                    : Json.comoObjeto(requisicao.get("dados"));

            Object corpo = switch (operacao) {
                case LISTAR_ARTISTAS -> listarArtistas();
                case LISTAR_USUARIOS -> listarUsuarios();
                case OBTER_USUARIO -> obterUsuario(dados);
                case CADASTRAR_USUARIO -> cadastrarUsuario(dados);
                case AVALIAR -> avaliar(dados);
                case RECOMENDAR -> recomendar(dados);
            };
            return Json.escrever(Json.objeto("ok", true, "dados", corpo));

        } catch (JsonException | IllegalArgumentException | IllegalStateException e) {
            return Json.escrever(Json.objeto("ok", false, "erro", e.getMessage()));
        } catch (RuntimeException e) {
            return Json.escrever(Json.objeto("ok", false, "erro", "Falha ao processar a requisição: " + e));
        }
    }

    // ------------------------------------------------------------------
    // Operações
    // ------------------------------------------------------------------

    private Map<String, Object> listarArtistas() {
        List<Object> artistas = new ArrayList<>();
        for (Artista artista : base.artistas()) {
            artistas.add(Json.objeto("id", artista.id(), "nome", artista.nome()));
        }
        return Json.objeto("artistas", artistas);
    }

    private Map<String, Object> listarUsuarios() {
        List<Object> usuarios = new ArrayList<>();
        for (Usuario usuario : base.usuarios()) {
            usuarios.add(usuarioComoJson(usuario));
        }
        return Json.objeto("usuarios", usuarios);
    }

    private Map<String, Object> obterUsuario(Map<String, Object> dados) {
        Usuario usuario = base.exigirUsuario(Json.texto(dados, "nome"));
        return Json.objeto("usuario", usuarioComoJson(usuario));
    }

    private Map<String, Object> cadastrarUsuario(Map<String, Object> dados) {
        String nome = Json.texto(dados, "nome");
        List<Object> notasRecebidas = Json.arranjo(dados, "avaliacoes");
        if (notasRecebidas == null || notasRecebidas.size() != Usuario.TOTAL_ARTISTAS) {
            throw new IllegalArgumentException(
                    "Envie exatamente " + Usuario.TOTAL_ARTISTAS + " avaliações no campo 'avaliacoes'");
        }
        int[] avaliacoes = new int[Usuario.TOTAL_ARTISTAS];
        for (int i = 0; i < avaliacoes.length; i++) {
            avaliacoes[i] = ((Number) notasRecebidas.get(i)).intValue();
        }
        Usuario novo = base.cadastrarUsuario(nome, avaliacoes);
        ServidorRecomendacao.registrar("Novo usuário cadastrado: " + novo.nome());
        return Json.objeto("usuario", usuarioComoJson(novo));
    }

    private Map<String, Object> avaliar(Map<String, Object> dados) {
        Usuario usuario = base.exigirUsuario(Json.texto(dados, "usuario"));
        int codigoArtista = Json.inteiro(dados, "artista", -1);
        int nota = Json.inteiro(dados, "nota", -1);

        Artista artista = base.artistaPorId(codigoArtista);
        Usuario.validarNota(nota);
        usuario.avaliar(codigoArtista - 1, nota);

        ServidorRecomendacao.registrar(usuario.nome() + " avaliou " + artista.nome() + " com nota " + nota);
        return Json.objeto("usuario", usuarioComoJson(usuario));
    }

    private Map<String, Object> recomendar(Map<String, Object> dados) {
        Usuario alvo = base.exigirUsuario(Json.texto(dados, "usuario"));
        int quantidadeVizinhos = Json.inteiro(dados, "vizinhos", MotorDeRecomendacao.VIZINHOS_PADRAO);
        int maxRecomendacoes = Json.inteiro(dados, "maxRecomendacoes", MotorDeRecomendacao.RECOMENDACOES_PADRAO);

        List<Object> vizinhos = new ArrayList<>();
        for (MotorDeRecomendacao.Vizinho vizinho : motor.vizinhosMaisSemelhantes(alvo, quantidadeVizinhos)) {
            vizinhos.add(Json.objeto(
                    "nome", vizinho.usuario().nome(),
                    "distancia", arredondar(vizinho.distancia()),
                    "artistasEmComum", vizinho.artistasEmComum()));
        }

        List<Object> recomendacoes = new ArrayList<>();
        for (MotorDeRecomendacao.Recomendacao recomendacao
                : motor.recomendar(alvo, quantidadeVizinhos, maxRecomendacoes)) {
            recomendacoes.add(Json.objeto(
                    "id", recomendacao.artista().id(),
                    "nome", recomendacao.artista().nome(),
                    "notaPrevista", arredondar(recomendacao.notaPrevista()),
                    "baseadoEm", new ArrayList<Object>(recomendacao.baseadoEm())));
        }

        return Json.objeto(
                "usuario", alvo.nome(),
                "vizinhosMaisSemelhantes", vizinhos,
                "recomendacoes", recomendacoes);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private static Map<String, Object> usuarioComoJson(Usuario usuario) {
        List<Object> notas = new ArrayList<>();
        for (int nota : usuario.avaliacoes()) {
            notas.add(nota);
        }
        return Json.objeto("nome", usuario.nome(), "avaliacoes", notas);
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 1000.0) / 1000.0;
    }
}
