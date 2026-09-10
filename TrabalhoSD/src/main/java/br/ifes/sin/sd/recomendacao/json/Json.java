package br.ifes.sin.sd.recomendacao.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementação mínima de JSON (subconjunto da RFC 8259) usada na comunicação
 * cliente/servidor.
 *
 * <p>A opção por uma implementação própria — em vez de bibliotecas como Gson ou
 * Jackson — mantém o trabalho <b>sem dependências externas</b>, de modo que ele
 * compile e execute em qualquer máquina que possua apenas um JDK instalado.</p>
 *
 * <p>Mapeamento de tipos JSON para Java:</p>
 * <pre>
 *   objeto  -&gt; java.util.LinkedHashMap&lt;String,Object&gt;
 *   arranjo -&gt; java.util.ArrayList&lt;Object&gt;
 *   texto   -&gt; java.lang.String
 *   número  -&gt; java.lang.Double
 *   boolean -&gt; java.lang.Boolean
 *   null    -&gt; null
 * </pre>
 */
public final class Json {

    private final String origem;
    private int pos;

    private Json(String origem) {
        this.origem = origem;
    }

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    /** Converte um texto JSON em objetos Java (Map, List, String, Double, Boolean, null). */
    public static Object ler(String texto) {
        Json leitor = new Json(texto);
        leitor.pularBrancos();
        Object valor = leitor.lerValor();
        leitor.pularBrancos();
        if (leitor.pos != texto.length()) {
            throw new JsonException("Conteúdo inesperado após o valor JSON (posição " + leitor.pos + ")");
        }
        return valor;
    }

    /** Serializa um objeto Java para texto JSON compacto. */
    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    /** Cria um objeto JSON (mantém a ordem de inserção) a partir de pares chave/valor. */
    public static Map<String, Object> objeto(Object... paresChaveValor) {
        if (paresChaveValor.length % 2 != 0) {
            throw new IllegalArgumentException("Informe pares chave/valor");
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            mapa.put(String.valueOf(paresChaveValor[i]), paresChaveValor[i + 1]);
        }
        return mapa;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> comoObjeto(Object valor) {
        if (!(valor instanceof Map<?, ?>)) {
            throw new JsonException("Esperava um objeto JSON");
        }
        return (Map<String, Object>) valor;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arranjo(Map<String, Object> objeto, String chave) {
        Object valor = objeto.get(chave);
        return valor == null ? null : (List<Object>) valor;
    }

    public static String texto(Map<String, Object> objeto, String chave) {
        Object valor = objeto.get(chave);
        return valor == null ? null : valor.toString();
    }

    public static int inteiro(Map<String, Object> objeto, String chave, int padrao) {
        Object valor = objeto.get(chave);
        if (valor == null) {
            return padrao;
        }
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        return Integer.parseInt(valor.toString().trim());
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    private Object lerValor() {
        char c = atual();
        return switch (c) {
            case '{' -> lerObjeto();
            case '[' -> lerArranjo();
            case '"' -> lerTexto();
            case 't', 'f' -> lerBooleano();
            case 'n' -> lerNulo();
            default -> lerNumero();
        };
    }

    private Map<String, Object> lerObjeto() {
        Map<String, Object> objeto = new LinkedHashMap<>();
        pos++; // consome '{'
        pularBrancos();
        if (atual() == '}') {
            pos++;
            return objeto;
        }
        while (true) {
            pularBrancos();
            if (atual() != '"') {
                throw new JsonException("Esperava uma chave de texto na posição " + pos);
            }
            String chave = lerTexto();
            pularBrancos();
            if (atual() != ':') {
                throw new JsonException("Esperava ':' na posição " + pos);
            }
            pos++;
            pularBrancos();
            objeto.put(chave, lerValor());
            pularBrancos();
            char c = atual();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return objeto;
            }
            throw new JsonException("Esperava ',' ou '}' na posição " + pos);
        }
    }

    private List<Object> lerArranjo() {
        List<Object> lista = new ArrayList<>();
        pos++; // consome '['
        pularBrancos();
        if (atual() == ']') {
            pos++;
            return lista;
        }
        while (true) {
            pularBrancos();
            lista.add(lerValor());
            pularBrancos();
            char c = atual();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return lista;
            }
            throw new JsonException("Esperava ',' ou ']' na posição " + pos);
        }
    }

    private String lerTexto() {
        StringBuilder sb = new StringBuilder();
        pos++; // consome aspas de abertura
        while (true) {
            if (pos >= origem.length()) {
                throw new JsonException("Texto JSON não terminado");
            }
            char c = origem.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char escape = origem.charAt(pos++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = origem.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> throw new JsonException("Sequência de escape inválida: \\" + escape);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double lerNumero() {
        int inicio = pos;
        while (pos < origem.length() && "+-0123456789.eE".indexOf(origem.charAt(pos)) >= 0) {
            pos++;
        }
        String bruto = origem.substring(inicio, pos);
        try {
            return Double.parseDouble(bruto);
        } catch (NumberFormatException e) {
            throw new JsonException("Número inválido: '" + bruto + "'");
        }
    }

    private Boolean lerBooleano() {
        if (origem.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (origem.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("Valor booleano inválido na posição " + pos);
    }

    private Object lerNulo() {
        if (origem.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonException("Valor inválido na posição " + pos);
    }

    private char atual() {
        if (pos >= origem.length()) {
            throw new JsonException("Fim inesperado do texto JSON");
        }
        return origem.charAt(pos);
    }

    private void pularBrancos() {
        while (pos < origem.length() && Character.isWhitespace(origem.charAt(pos))) {
            pos++;
        }
    }

    // ------------------------------------------------------------------
    // Escrita
    // ------------------------------------------------------------------

    private static void escrever(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escreverTexto(String.valueOf(entrada.getKey()), sb);
                sb.append(':');
                escrever(entrada.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> iteravel) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : iteravel) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escrever(item, sb);
            }
            sb.append(']');
        } else if (valor instanceof int[] vetor) {
            sb.append('[');
            for (int i = 0; i < vetor.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(vetor[i]);
            }
            sb.append(']');
        } else if (valor instanceof String texto) {
            escreverTexto(texto, sb);
        } else if (valor instanceof Double numero) {
            if (!numero.isInfinite() && !numero.isNaN() && numero == Math.rint(numero)) {
                sb.append(numero.longValue());
            } else {
                sb.append(numero.toString());
            }
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else {
            escreverTexto(valor.toString(), sb);
        }
    }

    private static void escreverTexto(String texto, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
