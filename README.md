# Brisa: rede de cuidado para idosos em ondas de calor (MVP)

Global Solution 2026.2 (CitizenTech), 2º ano de Sistemas de Informação, FIAP, turma 2SIR.
Equipe Brisa: Vitor Ferreira Diamantino (RM 561902), Felype Souza Santos (RM 565656), Gabriel Ribeiro (RM 565241).

A Brisa transforma o alerta de calor em confirmação. Ela calcula todo dia o Índice de Risco Térmico (IRT) de cada
idoso, pergunta pelo Telegram se ele está bem e, se ninguém responder, aciona a rede de cuidado em ordem: família,
vizinho guardião e agente de saúde da UBS.

## O que o MVP faz (RF01 a RF09)

| Requisito | Onde ver |
|---|---|
| RF01 Cadastro do idoso, com consentimento LGPD | menu **Cadastrar idoso** |
| RF02 Rede de cuidado ordenada | página do idoso, "Rede de cuidado" |
| RF03 Previsão por CEP | BrasilAPI (CEP para coordenadas) + Open-Meteo (sensação térmica máxima do dia) |
| RF04 Cálculo diário do IRT | todo dia às 6h, ou no botão **Rodar o ciclo das 6h** |
| RF05 Check-in com 2 botões | Telegram: "Estou bem" e "Preciso de ajuda" |
| RF06 Registro de cada resposta | linha do tempo "O que aconteceu" e tabela `BRISA_CHECKIN` |
| RF07 Escalonamento automático | lembrete aos 30 min, 1º contato aos 60 min, depois um contato a cada 30 min |
| RF08 Botão Preciso de ajuda | avisa toda a rede e orienta ligar 192 |
| RF09 Painel com semáforo | página inicial: semáforo e lista em que quem está sem resposta sobe para o topo |
| RF10 Sensor de casa | simulado: campo na página do idoso ou `POST /api/sensor/{id}` |

`IRT = 0,40 × Calor previsto + 0,25 × Calor em casa + 0,25 × Vulnerabilidade + 0,10 × Histórico`, com cada
componente de 0 a 100. Os níveis são verde (0 a 39), amarelo (40 a 59), laranja (60 a 79) e vermelho (80 a 100).
O IRT organiza o cuidado e não faz diagnóstico.

## Tecnologias

Java 21, Spring Boot 3.5 (Web, Thymeleaf, Data JPA, Validation), Oracle da FIAP, Telegram Bot API (long polling),
Open-Meteo, BrasilAPI e JUnit 5. As estruturas de dados das aulas aparecem assim:
- o ciclo das 6h envia os check-ins por uma **fila de prioridade** (`PriorityQueue`), do maior IRT para o menor;
- o escalonamento percorre a rede de cuidado como uma **lista ligada** (`LinkedList`).

## Como rodar (qualquer PC com Windows)

1. Instale o **Java 21** (https://adoptium.net, "Temurin 21 LTS"). Para conferir, rode `java -version` no terminal.
2. Baixe o projeto: `git clone https://github.com/vitordiamantino/brisa-mvp.git`. Também dá para baixar o ZIP pelo
   GitHub, no botão "Code" e depois "Download ZIP".
3. Na pasta do projeto, copie `config-local.exemplo.properties` para **`config-local.properties`** e preencha:
   - `DB_USER` e `DB_PASSWORD`: o seu login do Oracle da FIAP (rm + número e a senha);
   - `TELEGRAM_TOKEN`: o token do bot. Sem ele, tudo funciona e as mensagens aparecem como "simuladas" no painel.
4. Dê dois cliques em **`rodar.cmd`**. Na primeira vez, ele baixa as dependências, o que leva alguns minutos.
5. Quando o terminal mostrar `Started BrisaApplication`, abra **http://localhost:8080**.

Na primeira execução, a Brisa cria as tabelas `BRISA_*` e os dados de demonstração: Dona Lurdes e mais quatro
idosos de Itaquera, todos fictícios.

**Importante:** só uma máquina pode rodar a Brisa com o mesmo bot ao mesmo tempo. Se duas rodarem juntas, o
Telegram entrega as mensagens para só uma delas.

## Roteiro da demonstração (vídeo)

1. **Painel:** o semáforo e a previsão real do dia. No campo "°C", digite `38` e clique em **Simular onda**. Depois,
   clique em **Rodar o ciclo das 6h**: o IRT de todos sobe e os idosos em laranja e vermelho recebem o check-in.
2. **Telegram (celular):** a Dona Lurdes recebe a mensagem com os dois botões. Deixe sem responder.
3. **Escalonamento:** na demonstração, 1 minuto do protocolo dura 1 segundo. Em 30 s sai o lembrete; em 60 s a
   filha (Carla) recebe o aviso no Telegram e toca em "Já falei, está tudo bem". O painel mostra quem confirmou.
4. **Preciso de ajuda:** num segundo idoso, toque em "Preciso de ajuda". Toda a rede é avisada, e ele sobe para o
   topo do painel em vermelho.
5. **Página do idoso:** o "por que o risco está assim" (os 4 componentes) e a temperatura da casa contra a da rua.
   Simule o sensor com `34` °C e mostre o IRT subindo.
6. **Cadastro:** cadastre um idoso novo com CEP, saúde, telhado, rede de cuidado e consentimento LGPD.
7. **Banco:** no SQL Developer, rode `SELECT * FROM BRISA_CHECKIN ORDER BY ENVIADO_EM DESC;` e
   `SELECT * FROM BRISA_EVENTO ORDER BY QUANDO DESC;`.

### Vincular o Telegram (uma vez)

Na página de cada idoso ou contato, o link **"convite do Telegram"** abre o bot com `/start idoso-N` ou
`/start contato-N`. Abra pelo celular de quem vai "ser" aquela pessoa no vídeo e toque em **Começar**. Os links só
aparecem com o `TELEGRAM_TOKEN` configurado.

### Para recomeçar do zero

Para ensaiar de novo, use o botão **Recomeçar a demonstração**, no fim do painel. Ele apaga os check-ins e volta
aos dados iniciais, sem desfazer os vínculos do Telegram.

Para apagar as tabelas inteiras, rode no SQL Developer: `DROP TABLE BRISA_EVENTO; DROP TABLE BRISA_CHECKIN; DROP TABLE BRISA_CONTATO; DROP TABLE BRISA_IDOSO;`.
Ao rodar de novo, a Brisa recria tudo com os dados de demonstração.

## API REST

- `GET /api/idosos`: risco de todos, do maior IRT para o menor.
- `POST /api/sensor/{id}` com `{"temperatura": 33.4}`: leitura do sensor de casa, o mesmo contrato que o ESP32 usaria.
