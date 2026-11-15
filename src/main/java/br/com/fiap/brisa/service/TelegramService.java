package br.com.fiap.brisa.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cliente mínimo da Bot API do Telegram (long polling: roda em qualquer PC, sem endereço público). */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final String token;
    private final RestClient http = RestClient.create();
    private String usuarioBot;

    public record Botao(String texto, String dado) {}

    public TelegramService(@Value("${brisa.telegram.token:}") String token) {
        this.token = token == null ? "" : token.trim();
    }

    public boolean ativo() {
        return !token.isEmpty();
    }

    public String getUsuarioBot() {
        if (usuarioBot == null && ativo()) {
            try {
                usuarioBot = chamar("getMe", Map.of()).path("result").path("username").asText(null);
            } catch (Exception e) {
                log.warn("Telegram getMe falhou: {}", e.getMessage());
            }
        }
        return usuarioBot;
    }

    /** Envia texto com até uma linha de botões. Devolve false se não foi possível enviar. */
    public boolean enviar(Long chatId, String texto, List<Botao> botoes) {
        if (!ativo() || chatId == null) return false;
        try {
            Map<String, Object> corpo = new java.util.HashMap<>();
            corpo.put("chat_id", chatId);
            corpo.put("text", texto);
            if (botoes != null && !botoes.isEmpty()) {
                List<Map<String, String>> linha = new ArrayList<>();
                for (Botao b : botoes) linha.add(Map.of("text", b.texto(), "callback_data", b.dado()));
                corpo.put("reply_markup", Map.of("inline_keyboard", List.of(linha)));
            }
            chamar("sendMessage", corpo);
            return true;
        } catch (Exception e) {
            log.warn("Telegram sendMessage falhou para {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    public void responderBotao(String callbackId, String aviso) {
        try {
            chamar("answerCallbackQuery", Map.of("callback_query_id", callbackId, "text", aviso));
        } catch (Exception e) {
            log.debug("answerCallbackQuery falhou: {}", e.getMessage());
        }
    }

    public void removerBotoes(Long chatId, long messageId) {
        try {
            chamar("editMessageReplyMarkup", Map.of("chat_id", chatId, "message_id", messageId,
                    "reply_markup", Map.of("inline_keyboard", List.of())));
        } catch (Exception e) {
            log.debug("editMessageReplyMarkup falhou: {}", e.getMessage());
        }
    }

    public JsonNode buscarAtualizacoes(long offset) {
        return chamar("getUpdates", Map.of("offset", offset, "timeout", 20,
                "allowed_updates", List.of("message", "callback_query")));
    }

    private JsonNode chamar(String metodo, Map<String, Object> corpo) {
        try {
            return http.post().uri("https://api.telegram.org/bot{t}/{m}", token, metodo)
                    .contentType(MediaType.APPLICATION_JSON).body(corpo)
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException e) {
            // A mensagem original traz a URL com o token: nunca deixar isso chegar ao log.
            throw new IllegalStateException(String.valueOf(e.getMessage()).replace(token, "<token>"));
        }
    }
}
