package br.com.fiap.brisa.service;

import br.com.fiap.brisa.model.Contato;
import br.com.fiap.brisa.model.Idoso;
import br.com.fiap.brisa.repository.ContatoRepository;
import br.com.fiap.brisa.repository.IdosoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lê as atualizações do bot em uma thread própria (long polling) e trata:
 * /start idoso-ID e /start contato-ID para vincular o Telegram, e os botões dos check-ins.
 */
@Component
public class TelegramBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final TelegramService telegram;
    private final CuidadoService cuidado;
    private final IdosoRepository idosos;
    private final ContatoRepository contatos;
    private volatile boolean rodando = true;

    public TelegramBot(TelegramService telegram, CuidadoService cuidado, IdosoRepository idosos, ContatoRepository contatos) {
        this.telegram = telegram;
        this.cuidado = cuidado;
        this.idosos = idosos;
        this.contatos = contatos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (!telegram.ativo()) {
            log.info("Telegram desligado (sem TELEGRAM_TOKEN). As mensagens serão simuladas no painel.");
            return;
        }
        log.info("Bot do Telegram ativo: @{}", telegram.getUsuarioBot());
        Thread.ofVirtual().name("telegram-polling").start(this::laco);
    }

    private void laco() {
        long offset = 0;
        while (rodando) {
            try {
                JsonNode resp = telegram.buscarAtualizacoes(offset);
                for (JsonNode u : resp.path("result")) {
                    offset = u.path("update_id").asLong() + 1;
                    try {
                        tratar(u);
                    } catch (Exception e) {
                        log.warn("Falha ao tratar atualização do Telegram: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Telegram indisponível ({}). Se outra máquina estiver rodando a Brisa com o mesmo bot, feche uma delas.", e.getMessage());
                dormir(5000);
            }
        }
    }

    private void tratar(JsonNode u) {
        if (u.has("callback_query")) {
            JsonNode q = u.path("callback_query");
            long chatId = q.path("message").path("chat").path("id").asLong();
            long messageId = q.path("message").path("message_id").asLong();
            String[] partes = q.path("data").asText("").split(":");
            if (partes.length != 2) return;
            long checkInId = Long.parseLong(partes[1]);
            String resposta = switch (partes[0]) {
                case "bem" -> cuidado.responder(checkInId, true, " pelo Telegram");
                case "ajuda" -> cuidado.responder(checkInId, false, " pelo Telegram");
                case "conf" -> cuidado.confirmarPorContato(checkInId, chatId, nomeDoContato(chatId));
                default -> null;
            };
            if (resposta != null) {
                telegram.responderBotao(q.path("id").asText(), "Recebido");
                telegram.removerBotoes(chatId, messageId);
                telegram.enviar(chatId, resposta, List.of());
            }
            return;
        }
        JsonNode msg = u.path("message");
        String texto = msg.path("text").asText("");
        long chatId = msg.path("chat").path("id").asLong();
        if (texto.startsWith("/start")) {
            String param = texto.replace("/start", "").trim();
            telegram.enviar(chatId, vincular(param, chatId), List.of());
        }
    }

    private String vincular(String param, long chatId) {
        try {
            if (param.startsWith("idoso-")) {
                Idoso i = idosos.findById(Long.parseLong(param.substring(6))).orElseThrow();
                i.setTelegramChatId(chatId);
                idosos.save(i);
                cuidado.registrar(i, "TELEGRAM", "Telegram do idoso vinculado.");
                return "Olá, " + i.getNome() + "! Sou a Brisa. Nos dias de calor eu pergunto se você está bem.\nÉ só tocar no botão.";
            }
            if (param.startsWith("contato-")) {
                Contato c = contatos.findComIdosoById(Long.parseLong(param.substring(8))).orElseThrow();
                c.setTelegramChatId(chatId);
                contatos.save(c);
                cuidado.registrar(c.getIdoso(), "TELEGRAM", "Telegram de " + c.getNome() + " vinculado.");
                return "Olá, " + c.getNome() + "! Você entrou na rede de cuidado de " + c.getIdoso().getNome()
                        + ".\nSe não houver resposta num dia de calor, eu aviso você.";
            }
        } catch (Exception e) {
            log.warn("Vínculo inválido '{}': {}", param, e.getMessage());
        }
        return "Olá! Sou a Brisa. Para participar, abra o link de convite gerado no painel da UBS.";
    }

    private String nomeDoContato(long chatId) {
        List<Contato> lista = contatos.findByTelegramChatId(chatId);
        return lista.isEmpty() ? "Um contato" : lista.get(0).getNome();
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
