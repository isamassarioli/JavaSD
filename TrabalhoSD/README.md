# Sistema Distribuído de Recomendação Musical

Trabalho Avaliativo — Tópico 4
**Sistemas Distribuídos** — Bacharelado em Sistemas de Informação — Ifes Campus Colatina

Aplicação distribuída em Java que recomenda artistas/bandas a partir das
preferências informadas por diferentes usuários, usando arquitetura
**cliente/servidor**, comunicação **TCP/IP**, **servidor concorrente** e
**distância euclidiana** como medida de similaridade.

---

## 1. Visão geral da arquitetura

```
   Cliente 1 ─┐
   Cliente 2 ─┼──TCP/IP (JSON por linha)──►  Servidor concorrente
   Cliente N ─┘                                 │
                                                ├─ BaseDeDados (15 artistas, N usuários)
                                                └─ MotorDeRecomendacao (distância euclidiana)
```

- **Servidor central** (`ServidorRecomendacao`): mantém em memória os dados dos
  usuários e suas avaliações. Abre um `ServerSocket` e, para **cada** cliente,
  entrega o atendimento a uma **thread** de um pool
  (`Executors.newCachedThreadPool()`). Assim vários clientes são atendidos
  **simultaneamente**.
- **Atendente** (`AtendenteCliente`): um `Runnable` por conexão. Lê requisições
  JSON linha a linha e responde.
- **Cliente** (`ClienteRecomendacao`): aplicação de terminal com menu. Conecta
  por TCP, mantém a conexão aberta durante a sessão e permite listar artistas,
  cadastrar usuários, registrar avaliações e pedir recomendações.
- **Base de dados** (`BaseDeDados`): região crítica compartilhada. Usa
  `ConcurrentHashMap` para os usuários e delega a alteração do vetor de notas à
  classe `Usuario`, cujos métodos são `synchronized`.
- **Motor de recomendação** (`MotorDeRecomendacao`): calcula a distância
  euclidiana entre perfis e gera as recomendações.

O protocolo de mensagens está documentado em [PROTOCOLO.md](PROTOCOLO.md).

### Por que Maven (e por que ele é opcional aqui)

O projeto segue o **layout Maven** (`src/main/java`, `pom.xml`) porque é o
padrão da plataforma Java e o formato que qualquer IDE (IntelliJ, Eclipse,
VS Code, NetBeans) importa sem configuração — diferentemente do Gradle, cujas
vantagens (build incremental, DSL flexível) não fazem diferença num projeto
deste porte. Para o avaliador que já tem Maven: `mvn package` gera um jar
executável único.

Ainda assim, **a aplicação não tem nenhuma dependência externa** — o JSON é
implementado na classe `br.ifes.sin.sd.recomendacao.json.Json`. Por isso ela
compila e roda **somente com o JDK**, através dos scripts abaixo, sem precisar
instalar Maven na máquina.

---

## 2. Como compilar e executar

Requisito: **JDK 17 ou superior** (`java -version`).

### Windows

```bat
compilar.bat            :: compila para target\classes
servidor.bat            :: inicia o servidor na porta 5000
cliente.bat             :: inicia um cliente ligado a localhost:5000
testar.bat              :: roda os testes automáticos
```

Abra **um terminal para o servidor** e **um terminal para cada cliente**.
Para simular a rede, outro cliente pode apontar para o IP da máquina do
servidor: `cliente.bat 192.168.0.10 5000`.

### Linux / macOS

```bash
./build.sh compilar
./build.sh servidor           # porta 5000
./build.sh cliente            # localhost:5000
./build.sh teste
```

### Com Maven (opcional)

```bash
mvn package
java -jar target/recomendacao-musical.jar servidor
java -jar target/recomendacao-musical.jar cliente localhost 5000
```

> **Acentuação no Windows:** os `.bat` já executam `chcp 65001` para exibir
> corretamente nomes como "Legião Urbana" e "Titãs".

---

## 3. Dados iniciais

15 artistas/bandas (códigos 1 a 15) e 10 usuários, cada um com uma nota para
cada artista. Os perfis foram organizados em três grupos de afinidade — rock
clássico internacional, rock nacional e pop — e contêm vários zeros
propositais. Ver [`DadosIniciais.java`](src/main/java/br/ifes/sin/sd/recomendacao/servidor/DadosIniciais.java).

| Código | Artista/Banda | Código | Artista/Banda |
|-------:|---------------|-------:|---------------|
| 1 | The Beatles     | 9  | Coldplay          |
| 2 | Queen           | 10 | Imagine Dragons   |
| 3 | Pink Floyd      | 11 | Legião Urbana     |
| 4 | Led Zeppelin    | 12 | Titãs             |
| 5 | Nirvana         | 13 | Charlie Brown Jr. |
| 6 | Metallica       | 14 | Skank             |
| 7 | Iron Maiden     | 15 | Capital Inicial   |
| 8 | U2              |    |                   |

Notas possíveis:

| Nota | Significado |
|-----:|-------------|
| 0 | Não conheço ou ainda não foi avaliado |
| 1 | Não gosto |
| 2 | Gosto muito pouco |
| 3 | Gosto |
| 4 | Gosto muito |

---

## 4. Distância euclidiana

Para dois usuários `A` e `B`:

```
d(A, B) = raiz( Σ (A_i − B_i)² )
```

Quanto **menor** a distância, **mais parecidos** são os gostos.

### Tratamento das avaliações iguais a zero (regra implementada)

A nota `0` significa *"não conheço / ainda não avaliei"* e **não** é uma
preferência negativa. Portanto, conforme o item 5 do enunciado:

> No cálculo da distância entre dois usuários, só são consideradas as posições
> em que **ambos** possuem avaliação **diferente de zero**.

Se não houver nenhum artista avaliado pelos dois usuários, os perfis são
considerados **incomparáveis** e esse par é ignorado na recomendação
(`OptionalDouble.empty()` em `MotorDeRecomendacao.distancia`).

**Exemplo (item 5 do enunciado):**

```
Ana  = [4, 3, 0, 4, 2]
João = [3, 2, 4, 4, 0]
```

Comparados apenas The Beatles, Queen e Led Zeppelin (avaliados pelos dois):

```
d = raiz( (4−3)² + (3−2)² + (4−4)² ) = raiz(2) ≈ 1,414
```

Esse mesmo caso é verificado no teste automático
[`Testes.java`](src/main/java/br/ifes/sin/sd/recomendacao/Testes.java).

> Observação: o exemplo do item 4 do enunciado (que resultava em `2`) inclui uma
> posição `0×1` no cálculo. Ele foi apresentado **antes** da regra do item 5;
> seguindo a regra do item 5, que é a exigida, aquele par passa a resultar em
> `raiz(3)`.

---

## 5. Algoritmo de recomendação

Para um usuário-alvo:

1. Calcula-se a distância euclidiana (regra do zero acima) entre o alvo e todos
   os demais usuários que tenham ao menos um artista avaliado em comum.
2. Ordenam-se os usuários pela menor distância → **vizinhos mais semelhantes**.
   Por padrão consideram-se os **3** mais próximos (configurável pelo cliente).
3. Para cada artista que o alvo **ainda não avaliou** (nota 0), calcula-se a
   **média das notas dos vizinhos**, ponderada pela proximidade de cada vizinho:

   ```
   peso(vizinho)   = 1 / (1 + distância(alvo, vizinho))
   notaPrevista(a) = Σ ( peso(v) · nota(v, a) ) / Σ peso(v)
   ```

   (somente vizinhos que avaliaram o artista `a` entram na conta).
4. As recomendações são ordenadas da **maior** para a **menor** nota prevista;
   o cliente recebe as 5 primeiras por padrão.

O resultado devolvido ao cliente traz tanto os **vizinhos mais semelhantes**
(com a distância e o nº de artistas em comum) quanto a **lista de recomendações**
(com a nota prevista e em quais vizinhos ela se baseou).

---

## 6. Demonstração da concorrência

1. Inicie o servidor.
2. Abra **dois ou mais** clientes ao mesmo tempo.
3. Em um cliente faça `AVALIAR` (ex.: Ana avalia Nirvana com 4); em outro,
   `CADASTRAR_USUARIO` e depois `RECOMENDAR`.
4. No terminal do servidor aparecem linhas com o nome da **thread** que atendeu
   cada requisição (`pool-1-thread-1`, `pool-1-thread-2`, ...), mostrando o
   atendimento simultâneo. As alterações feitas por um cliente ficam
   imediatamente visíveis aos demais, pois a `BaseDeDados` é compartilhada.

---

## 7. Estrutura do projeto

```
TrabalhoSD/
├── pom.xml                         Projeto Maven (opcional)
├── compilar.bat / servidor.bat / cliente.bat / testar.bat   Scripts Windows
├── build.sh                        Script Linux/macOS
├── PROTOCOLO.md                    Especificação das mensagens
├── README.md
└── src/main/java/br/ifes/sin/sd/recomendacao/
    ├── App.java                    Ponto de entrada (servidor | cliente | teste)
    ├── Testes.java                 Testes automáticos (sem framework)
    ├── json/
    │   ├── Json.java               Parser/serializador JSON próprio
    │   └── JsonException.java
    ├── modelo/
    │   ├── Artista.java
    │   └── Usuario.java            Perfil musical (vetor de 15 notas)
    ├── protocolo/
    │   └── Operacao.java           Enum das operações do protocolo
    ├── servidor/
    │   ├── ServidorRecomendacao.java   ServerSocket + pool de threads
    │   ├── AtendenteCliente.java       Um Runnable por conexão
    │   ├── BaseDeDados.java            Repositório concorrente
    │   ├── DadosIniciais.java          Carga dos 15 artistas e 10 usuários
    │   └── MotorDeRecomendacao.java    Distância euclidiana + recomendações
    └── cliente/
        └── ClienteRecomendacao.java    Cliente de terminal com menu
```

---

## 8. Divisão sugerida do trabalho (grupo de 3)

| Integrante | Responsabilidade principal |
|-----------|-----------------------------|
| 1 | Servidor e concorrência: `ServidorRecomendacao`, `AtendenteCliente`, `BaseDeDados`; testes de carga com vários clientes. |
| 2 | Domínio e recomendação: `Usuario`, `Artista`, `MotorDeRecomendacao`, `DadosIniciais`, `Testes`; documentação da regra do zero. |
| 3 | Cliente e protocolo: `ClienteRecomendacao`, `Operacao`, `json/Json`, `PROTOCOLO.md`; roteiro e slides da apresentação. |

Todos participam da integração final e da apresentação.
