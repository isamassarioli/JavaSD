# Protocolo de comunicação

## Transporte

- **TCP/IP**, porta padrão **5000**.
- Codificação **UTF-8**.
- **Uma mensagem JSON por linha**, terminada por `\n`.
- A conexão fica **aberta durante toda a sessão** do cliente: ele pode enviar
  várias requisições na mesma conexão. Para cada requisição o servidor devolve
  **exatamente uma linha** JSON.

## Formato geral

### Requisição (cliente → servidor)

```json
{"operacao": "NOME_DA_OPERACAO", "dados": { ... }}
```

### Resposta (servidor → cliente)

Sucesso:

```json
{"ok": true, "dados": { ... }}
```

Erro:

```json
{"ok": false, "erro": "mensagem explicativa"}
```

## Operações

| Operação            | Campos em `dados`                                   | Retorno em `dados`                                                        |
|---------------------|----------------------------------------------------|--------------------------------------------------------------------------|
| `LISTAR_ARTISTAS`   | —                                                  | `artistas: [{id, nome}]`                                                  |
| `LISTAR_USUARIOS`   | —                                                  | `usuarios: [{nome, avaliacoes:[15]}]`                                     |
| `OBTER_USUARIO`     | `nome`                                              | `usuario: {nome, avaliacoes:[15]}`                                        |
| `CADASTRAR_USUARIO` | `nome`, `avaliacoes:[15]` (cada nota de 0 a 4)      | `usuario: {nome, avaliacoes:[15]}`                                        |
| `AVALIAR`           | `usuario`, `artista` (1–15), `nota` (0–4)           | `usuario: {nome, avaliacoes:[15]}`                                        |
| `RECOMENDAR`        | `usuario`, `vizinhos` (opc.), `maxRecomendacoes` (opc.) | `usuario`, `vizinhosMaisSemelhantes:[{nome, distancia, artistasEmComum}]`, `recomendacoes:[{id, nome, notaPrevista, baseadoEm:[nomes]}]` |

## Exemplos

```text
→ {"operacao":"LISTAR_ARTISTAS"}
← {"ok":true,"dados":{"artistas":[{"id":1,"nome":"The Beatles"}, ...]}}

→ {"operacao":"AVALIAR","dados":{"usuario":"Ana","artista":5,"nota":4}}
← {"ok":true,"dados":{"usuario":{"nome":"Ana","avaliacoes":[4,3,4,3,4,1,0,3,4,4,2,0,3,2,3]}}}

→ {"operacao":"RECOMENDAR","dados":{"usuario":"Ana","vizinhos":3}}
← {"ok":true,"dados":{"usuario":"Ana",
     "vizinhosMaisSemelhantes":[{"nome":"Carla","distancia":2.236,"artistasEmComum":12}, ...],
     "recomendacoes":[{"id":12,"nome":"Titãs","notaPrevista":1.292,"baseadoEm":["Carla","João","Maria"]}]}}

→ {"operacao":"RECOMENDAR","dados":{"usuario":"Fulano"}}
← {"ok":false,"erro":"Usuário não encontrado: Fulano"}
```

## Teste manual com telnet / nc

Como o protocolo é texto puro, dá para testar sem o cliente Java:

```
telnet localhost 5000
{"operacao":"LISTAR_USUARIOS"}
```
