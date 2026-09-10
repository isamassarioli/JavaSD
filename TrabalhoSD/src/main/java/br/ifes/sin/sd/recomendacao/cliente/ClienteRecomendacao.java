package br.ifes.sin.sd.recomendacao.cliente;

import br.ifes.sin.sd.recomendacao.json.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Cliente interativo de linha de comando. Conecta-se ao servidor por TCP/IP,
 * mantém a conexão aberta durante toda a sessão e oferece um menu para consultar
 * artistas, cadastrar usuários, registrar avaliações e pedir recomendações.
 *
 * <p>Vários clientes podem rodar ao mesmo tempo contra o mesmo servidor.</p>
 */
public final class ClienteRecomendacao {

    private final String host;
    private final int porta;

    private BufferedReader entradaRede;
    private BufferedWriter saidaRede;

    public ClienteRecomendacao(String host, int porta) {
        this.host = host;
        this.porta = porta;
    }

    public void executar() {
        try (Socket socket = new Socket(host, porta)) {
            entradaRede = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saidaRede = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            Scanner teclado = new Scanner(System.in);
            System.out.println("=======================================");
            System.out.println(" Cliente de Recomendacao Musical");
            System.out.println(" Conectado a " + host + ":" + porta);
            System.out.println("=======================================");

            boolean executando = true;
            while (executando) {
                exibirMenu();
                System.out.print("Opcao: ");
                String opcao = teclado.hasNextLine() ? teclado.nextLine().trim() : "0";
                try {
                    switch (opcao) {
                        case "1" -> listarArtistas();
                        case "2" -> listarUsuarios();
                        case "3" -> verAvaliacoesDeUsuario(teclado);
                        case "4" -> cadastrarUsuario(teclado);
                        case "5" -> avaliarArtista(teclado);
                        case "6" -> obterRecomendacoes(teclado);
                        case "0" -> executando = false;
                        default -> System.out.println(">> Opcao invalida.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println(">> Valor numerico invalido.");
                } catch (Exception e) {
                    System.out.println(">> Erro: " + e.getMessage());
                }
            }
            System.out.println("Cliente encerrado.");
        } catch (IOException e) {
            System.err.println("Nao foi possivel conectar em " + host + ":" + porta + " -> " + e.getMessage());
        }
    }

    private void exibirMenu() {
        System.out.println();
        System.out.println("---------------------------------------");
        System.out.println(" 1 - Listar artistas");
        System.out.println(" 2 - Listar usuarios");
        System.out.println(" 3 - Ver avaliacoes de um usuario");
        System.out.println(" 4 - Cadastrar novo usuario");
        System.out.println(" 5 - Avaliar um artista");
        System.out.println(" 6 - Obter recomendacoes");
        System.out.println(" 0 - Sair");
        System.out.println("---------------------------------------");
    }

    // ------------------------------------------------------------------
    // Comunicacao
    // ------------------------------------------------------------------

    /** Envia uma requisicao JSON e devolve o objeto "dados" da resposta. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requisitar(String operacao, Map<String, Object> dados) throws IOException {
        Map<String, Object> requisicao = Json.objeto("operacao", operacao, "dados", dados);
        saidaRede.write(Json.escrever(requisicao));
        saidaRede.write("\n");
        saidaRede.flush();

        String resposta = entradaRede.readLine();
        if (resposta == null) {
            throw new IOException("O servidor encerrou a conexao");
        }
        Map<String, Object> objeto = Json.comoObjeto(Json.ler(resposta));
        if (!Boolean.TRUE.equals(objeto.get("ok"))) {
            throw new IllegalStateException(String.valueOf(objeto.get("erro")));
        }
        Object corpo = objeto.get("dados");
        return corpo == null ? new LinkedHashMap<>() : (Map<String, Object>) corpo;
    }

    /** Converte um arranjo JSON de numeros (Double) em algo como "[4, 3, 0, 4, 2]". */
    @SuppressWarnings("unchecked")
    private static String notas(Object arranjo) {
        StringBuilder sb = new StringBuilder("[");
        List<Object> lista = (List<Object>) arranjo;
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(((Number) lista.get(i)).intValue());
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // Operacoes do menu
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void listarArtistas() throws IOException {
        Map<String, Object> dados = requisitar("LISTAR_ARTISTAS", Map.of());
        System.out.println("\nArtistas / bandas:");
        for (Object item : (List<Object>) dados.get("artistas")) {
            Map<String, Object> artista = (Map<String, Object>) item;
            System.out.printf("  %2d - %s%n", ((Number) artista.get("id")).intValue(), artista.get("nome"));
        }
    }

    @SuppressWarnings("unchecked")
    private void listarUsuarios() throws IOException {
        Map<String, Object> dados = requisitar("LISTAR_USUARIOS", Map.of());
        System.out.println("\nUsuarios cadastrados (perfil musical na ordem dos artistas):");
        for (Object item : (List<Object>) dados.get("usuarios")) {
            Map<String, Object> usuario = (Map<String, Object>) item;
            System.out.printf("  %-12s %s%n", usuario.get("nome"), notas(usuario.get("avaliacoes")));
        }
    }

    @SuppressWarnings("unchecked")
    private void verAvaliacoesDeUsuario(Scanner teclado) throws IOException {
        System.out.print("Nome do usuario: ");
        String nome = teclado.nextLine().trim();
        Map<String, Object> dados = requisitar("OBTER_USUARIO", Json.objeto("nome", nome));
        Map<String, Object> usuario = (Map<String, Object>) dados.get("usuario");
        System.out.println("\n" + usuario.get("nome") + " -> " + notas(usuario.get("avaliacoes")));
    }

    @SuppressWarnings("unchecked")
    private void cadastrarUsuario(Scanner teclado) throws IOException {
        System.out.print("Nome do novo usuario: ");
        String nome = teclado.nextLine().trim();
        System.out.println("Digite as 15 notas (0 a 4) separadas por espaco,");
        System.out.println("na mesma ordem da opcao 1 (Listar artistas):");
        String[] partes = teclado.nextLine().trim().split("\\s+");
        if (partes.length != 15) {
            System.out.println(">> Sao necessarias exatamente 15 notas.");
            return;
        }
        List<Object> avaliacoes = new ArrayList<>();
        for (String parte : partes) {
            avaliacoes.add(Integer.parseInt(parte));
        }
        Map<String, Object> dados = requisitar("CADASTRAR_USUARIO",
                Json.objeto("nome", nome, "avaliacoes", avaliacoes));
        Map<String, Object> usuario = (Map<String, Object>) dados.get("usuario");
        System.out.println("Usuario cadastrado: " + usuario.get("nome") + " -> " + notas(usuario.get("avaliacoes")));
    }

    @SuppressWarnings("unchecked")
    private void avaliarArtista(Scanner teclado) throws IOException {
        System.out.print("Seu nome de usuario: ");
        String usuario = teclado.nextLine().trim();
        System.out.print("Codigo do artista (1 a 15): ");
        int artista = Integer.parseInt(teclado.nextLine().trim());
        System.out.print("Nota (0 a 4): ");
        int nota = Integer.parseInt(teclado.nextLine().trim());

        Map<String, Object> dados = requisitar("AVALIAR",
                Json.objeto("usuario", usuario, "artista", artista, "nota", nota));
        Map<String, Object> atualizado = (Map<String, Object>) dados.get("usuario");
        System.out.println("Avaliacao registrada. Perfil atual de " + atualizado.get("nome")
                + ": " + notas(atualizado.get("avaliacoes")));
    }

    @SuppressWarnings("unchecked")
    private void obterRecomendacoes(Scanner teclado) throws IOException {
        System.out.print("Nome do usuario: ");
        String usuario = teclado.nextLine().trim();
        System.out.print("Quantos vizinhos considerar (Enter = 3): ");
        String vizinhos = teclado.nextLine().trim();

        Map<String, Object> pedido = Json.objeto("usuario", usuario);
        if (!vizinhos.isEmpty()) {
            pedido.put("vizinhos", Integer.parseInt(vizinhos));
        }

        Map<String, Object> dados = requisitar("RECOMENDAR", pedido);

        System.out.println("\nUsuarios com gostos mais semelhantes a " + dados.get("usuario") + ":");
        List<Object> semelhantes = (List<Object>) dados.get("vizinhosMaisSemelhantes");
        if (semelhantes.isEmpty()) {
            System.out.println("  (nenhum usuario possui artistas avaliados em comum)");
        }
        for (Object item : semelhantes) {
            Map<String, Object> vizinho = (Map<String, Object>) item;
            System.out.printf("  %-12s distancia = %-7s (%d artistas em comum)%n",
                    vizinho.get("nome"), vizinho.get("distancia"),
                    ((Number) vizinho.get("artistasEmComum")).intValue());
        }

        List<Object> recomendacoes = (List<Object>) dados.get("recomendacoes");
        if (recomendacoes.isEmpty()) {
            System.out.println("\nSem recomendacoes no momento: o usuario ja avaliou todos os");
            System.out.println("artistas ou os vizinhos nao avaliaram nada que ele desconheca.");
            return;
        }
        System.out.println("\nRecomendacoes (da maior para a menor nota prevista):");
        for (Object item : recomendacoes) {
            Map<String, Object> recomendacao = (Map<String, Object>) item;
            System.out.printf("  %-18s nota prevista = %-5s  (baseado em: %s)%n",
                    recomendacao.get("nome"), recomendacao.get("notaPrevista"), recomendacao.get("baseadoEm"));
        }
    }
}
