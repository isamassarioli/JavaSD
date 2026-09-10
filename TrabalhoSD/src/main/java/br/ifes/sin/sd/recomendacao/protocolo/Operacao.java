package br.ifes.sin.sd.recomendacao.protocolo;

/**
 * Operações aceitas pelo servidor. O nome da constante é exatamente o valor
 * enviado no campo {@code "operacao"} de cada requisição JSON.
 */
public enum Operacao {

    /** Lista os 15 artistas/bandas e seus códigos. */
    LISTAR_ARTISTAS,

    /** Lista todos os usuários e seus perfis musicais. */
    LISTAR_USUARIOS,

    /** Retorna o perfil de um usuário específico. Dados: {@code {"nome": "..."}}. */
    OBTER_USUARIO,

    /** Cria um novo usuário. Dados: {@code {"nome": "...", "avaliacoes": [15 notas]}}. */
    CADASTRAR_USUARIO,

    /** Registra a avaliação de um artista. Dados: {@code {"usuario": "...", "artista": 1..15, "nota": 0..4}}. */
    AVALIAR,

    /**
     * Gera recomendações para um usuário.
     * Dados: {@code {"usuario": "...", "vizinhos": (opcional), "maxRecomendacoes": (opcional)}}.
     */
    RECOMENDAR;

    public static Operacao de(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Campo 'operacao' é obrigatório");
        }
        try {
            return Operacao.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Operação desconhecida: " + texto);
        }
    }
}
