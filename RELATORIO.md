# Relatório — Sistema Distribuído com Contract Net Protocol (UDP)

**Disciplina:** Sistemas Distribuídos · **Integrantes:** _(preencher — grupo de 4)_

---

## 1. Visão geral

O sistema é composto por dois tipos de processo que se comunicam exclusivamente
por **datagramas UDP** (`DatagramSocket` / `DatagramPacket`):

| Processo | Papel | Porta |
|----------|-------|-------|
| `Gerente` | Leiloeiro: gera as tarefas e conduz as 4 fases do CNP para cada uma | efêmera |
| `Agente` (3+ instâncias) | Contratado autônomo: propõe custo e executa a tarefa vencida | 9001, 9002, 9003, … |

Cada `Agente` mantém uma variável `cargaAtual` (carga de trabalho simulada,
inicial de 1 a 10). **Após executar** uma tarefa ele faz `cargaAtual++`, ficando
mais caro nas próximas rodadas — é isso que produz o balanceamento observável.

Classes auxiliares: `Protocolo` (constantes e parsing das mensagens) e `Tarefa`
(encapsula o cálculo — FATORIAL de N com `BigInteger` ou soma dos N primeiros
primos — de modo que Gerente e Agente concordem sobre o resultado).

---

## 2. Protocolo de mensagens

Formato **textual**, campos separados por `;`. Primeiro campo = tipo da mensagem.

| # | Fase CNP | Mensagem | Formato | Exemplo | Direção |
|---|----------|----------|---------|---------|---------|
| 1 | Anúncio | `CFP` | `CFP;<idTarefa>;<tipo>;<carga>` | `CFP;3;FATORIAL;18` | Gerente → todos os Agentes |
| 2 | Proposta | `PROPOSTA` | `PROPOSTA;<idTarefa>;<idAgente>;<custoEstimado>` | `PROPOSTA;3;9001;54` | Agente → Gerente |
| 3 | Adjudicação | `ADJUDICACAO` | `ADJUDICACAO;<idTarefa>;<tipo>;<carga>;<idAgente>` | `ADJUDICACAO;3;FATORIAL;18;9001` | Gerente → vencedor |
| 3 | Rejeição | `REJEICAO` | `REJEICAO;<idTarefa>;<idAgente>` | `REJEICAO;3;9002` | Gerente → perdedores |
| 4 | Resultado | `RESULTADO` | `RESULTADO;<idTarefa>;<idAgente>;<valor>;<tempoMs>` | `RESULTADO;3;9001;6402373705728000;2` | vencedor → Gerente |

Campos:
- `idTarefa` — inteiro único e sequencial gerado pelo Gerente.
- `tipo` — `FATORIAL` ou `SOMA_PRIMOS`.
- `carga` — o **N** do cálculo; também é o `fator_da_tarefa` usado no custo.
  Vai repetido na `ADJUDICACAO` para o agente reconstruir a tarefa sem manter estado.
- `idAgente` — a porta UDP do agente, que serve de identificador.
- `custoEstimado` — ver seção 4.
- `valor` — resultado do cálculo (pode ter dezenas de dígitos).
- `tempoMs` — tempo de execução medido pelo agente.

---

## 3. As 4 fases do CNP (implementação)

1. **CFP** — `Gerente.processarTarefa()` envia um `CFP` a cada endereço da lista
   fixa de agentes (`for` sobre `agentes`, um `socket.send` por agente).
2. **Coleta de propostas com _timeout_** — `Gerente.coletarPropostas()` calcula o
   instante-limite (`agora + 3000 ms`) e faz um laço `receive()` recalculando o
   tempo restante e chamando `socket.setSoTimeout(restante)` a cada iteração.
   Quando estoura, uma `SocketTimeoutException` é capturada, o log
   `timeout de propostas atingido` é emitido e o Gerente segue — **nunca trava**.
   Propostas de tarefas antigas (id diferente) são descartadas.
3. **Adjudicação / Rejeição** — escolhido o vencedor (seção 4), envia
   `ADJUDICACAO` a ele e `REJEICAO` aos demais proponentes.
4. **Resultado** — `Gerente.aguardarResultado()` espera o `RESULTADO` do vencedor
   com `setSoTimeout(15000)`; se não chegar, registra a falha e passa à próxima
   tarefa. O agente, ao receber `ADJUDICACAO`, executa `Tarefa.executar()`, envia
   `RESULTADO` e incrementa a própria carga.

Se **nenhuma** proposta chega (todos os agentes fora do ar), a tarefa é apenas
descartada naquela rodada, com log explícito.

---

## 4. Critério de seleção da melhor proposta

Cada agente calcula, ao receber o `CFP`:

```
custo_estimado = carga_atual * carga_da_tarefa      (fator_da_tarefa = carga_da_tarefa)
```

O Gerente (`escolherMelhor()`) percorre a lista de propostas e mantém a de
**menor `custo_estimado`**. **Critério de desempate documentado:** havendo empate
no custo, vence o agente de **menor número de porta** (`p.porta < melhor.porta`).
É determinístico, independe da ordem de chegada dos datagramas e é fácil de
conferir no log (ex.: tarefas 3, 5 e 7 abaixo terminam empatadas e vão para 9001).

---

## 5. Evidência de execução

Execução real com **3 agentes** (`9001` carga 1, `9002` carga 3, `9003` carga 5)
e **7 tarefas**. Log completo em `docs_run_gerente.txt` / `docs_run_agente9001.txt`.
Trecho e resumo da distribuição:

```
[GERENTE] Nova rodada -> Tarefa#1 [FATORIAL, carga=12]
[GERENTE] Fase 1 -> CFP enviado a 3 agente(s): CFP;1;FATORIAL;12
[GERENTE] Fase 2 <- PROPOSTA do agente 9001 custo=12
[GERENTE] Fase 2 <- PROPOSTA do agente 9003 custo=60
[GERENTE] Fase 2 <- PROPOSTA do agente 9002 custo=36
[GERENTE] Fase 2 -> timeout de propostas atingido.
[GERENTE] Fase 3 -> vencedor: agente 9001 com custo 12
[GERENTE] Fase 3 -> ADJUDICACAO enviada ao agente 9001
[GERENTE] Fase 3 -> REJEICAO enviada ao agente 9003
[GERENTE] Fase 3 -> REJEICAO enviada ao agente 9002
[GERENTE] Fase 4 <- RESULTADO da tarefa 1 pelo agente 9001 em 1ms
[GERENTE] Fase 4 -> valor calculado: 479001600
...
[GERENTE] Nova rodada -> Tarefa#4 [SOMA_PRIMOS, carga=5000]
[GERENTE] Fase 2 <- PROPOSTA do agente 9001 custo=20000
[GERENTE] Fase 2 <- PROPOSTA do agente 9002 custo=15000
[GERENTE] Fase 2 <- PROPOSTA do agente 9003 custo=25000
[GERENTE] Fase 3 -> vencedor: agente 9002 com custo 15000
[GERENTE] Fase 4 <- RESULTADO da tarefa 4 pelo agente 9002 em 4ms
```

| Tarefa | Tipo | Propostas (porta:custo) | Vencedor | Motivo |
|--------|------|-------------------------|----------|--------|
| 1 | FATORIAL 12 | 9001:12 · 9002:36 · 9003:60 | **9001** | menor custo |
| 2 | SOMA_PRIMOS 2000 | 9001:4000 · 9002:6000 · 9003:10000 | **9001** | menor custo |
| 3 | FATORIAL 18 | 9001:54 · 9002:54 · 9003:90 | **9001** | empate → menor porta |
| 4 | SOMA_PRIMOS 5000 | 9001:20000 · 9002:15000 · 9003:25000 | **9002** | 9001 já subiu de carga |
| 5 | FATORIAL 25 | 9001:100 · 9002:100 · 9003:125 | **9001** | empate → menor porta |
| 6 | SOMA_PRIMOS 8000 | 9001:40000 · 9002:32000 · 9003:40000 | **9002** | menor custo |
| 7 | FATORIAL 30 | 9001:150 · 9002:150 · 9003:150 | **9001** | empate → menor porta |

O agente 9001 começa mais ocioso e ganha as primeiras tarefas; à medida que sua
carga sobe (1 → 2 → 3 → 4 …), as tarefas maiores (4 e 6) migram para 9002 —
**balanceamento de carga observável** ao longo das rodadas.

---

## 6. Boas práticas adotadas

- Apenas `DatagramSocket` / `DatagramPacket`; nenhuma biblioteca de mensageria.
- Código dividido em métodos coesos (uma responsabilidade por método); nada de
  `main` gigante.
- Tratamento explícito de `IOException` e `SocketTimeoutException` em todas as
  operações de rede.
- Comentários marcando cada fase do CNP no código.
- Logs padronizados com prefixo `[GERENTE]` / `[AGENTE <porta>]` e a fase.
