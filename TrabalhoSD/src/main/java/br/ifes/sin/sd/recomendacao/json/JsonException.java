package br.ifes.sin.sd.recomendacao.json;

/** Erro lançado quando um texto JSON está mal formado. */
public class JsonException extends RuntimeException {
    public JsonException(String mensagem) {
        super(mensagem);
    }
}
