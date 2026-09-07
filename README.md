# JavaSD — Contract Net Protocol sobre UDP

Sistema distribuído com dois tipos de processo (`Gerente` e `Agente`) que
distribui tarefas computacionais usando as 4 fases do **Contract Net Protocol**
(CNP) e apenas `DatagramSocket` / `DatagramPacket` (sem bibliotecas externas).

## Estrutura

```
src/
  Protocolo.java   -> constantes e (de)serialização do protocolo textual
  Tarefa.java      -> tarefa computacional (FATORIAL / SOMA_PRIMOS) + execução
  Agente.java      -> processo agente autônomo (uma porta UDP por instância)
  Gerente.java     -> processo gerente (leiloeiro das tarefas)
RELATORIO.md       -> relatório de entrega
docs_run_*.txt     -> logs de uma execução real (evidência)
```

## Compilar

```bash
javac -d build/classes src/*.java
```

No NetBeans: abrir a pasta como projeto (*Java com Ant*) e usar **Build**.
O `main.class` do projeto é `Gerente`.

## Executar (4 processos Java separados)

Abrir **4 terminais** (ou 4 execuções no NetBeans). Primeiro os agentes:

```bash
java -cp build/classes Agente 9001 1
java -cp build/classes Agente 9002 3
java -cp build/classes Agente 9003 5
```

O segundo argumento (carga inicial) é opcional — se omitido, cada agente
sorteia um valor de 1 a 10. Depois o gerente:

```bash
java -cp build/classes Gerente
```

Portas alternativas: `java -cp build/classes Gerente 9001 9002 9003 9004`.

## Teste de timeout / agente ausente

Basta iniciar o `Gerente` com menos agentes no ar (ou nenhum). Ele registra
`timeout de propostas atingido` e segue para a próxima tarefa sem travar,
graças ao `setSoTimeout` na fase de coleta de propostas.
