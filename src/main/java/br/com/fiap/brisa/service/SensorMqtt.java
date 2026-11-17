package br.com.fiap.brisa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Recebe a temperatura de dentro de casa enviada pelo ESP32 + DHT22 (simulado no Wokwi) por MQTT.
 * Tópico brisa/fiap561902/casa/{idosoId}, payload {"temperatura": 36.0}.
 */
@Component
public class SensorMqtt implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(SensorMqtt.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CuidadoService cuidado;
    private final String broker;
    private final String topico;
    private MqttClient cliente;

    public SensorMqtt(CuidadoService cuidado,
                      @Value("${brisa.mqtt.broker:}") String broker,
                      @Value("${brisa.mqtt.topico:brisa/fiap561902/casa/+}") String topico) {
        this.cuidado = cuidado;
        this.broker = broker;
        this.topico = topico;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (broker == null || broker.isBlank()) {
            log.info("MQTT desligado (brisa.mqtt.broker vazio).");
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                cliente = new MqttClient(broker, "brisa-" + UUID.randomUUID(), new MemoryPersistence());
                cliente.setCallback(this);
                MqttConnectOptions opcoes = new MqttConnectOptions();
                opcoes.setAutomaticReconnect(true);
                opcoes.setCleanSession(true);
                cliente.connect(opcoes);
            } catch (MqttException e) {
                log.warn("MQTT: não conectou em {} ({}). O sensor continua disponível pela API e pelo painel.", broker, e.getMessage());
            }
        });
    }

    @Override
    public void connectComplete(boolean reconexao, String uri) {
        try {
            cliente.subscribe(topico, 1);
            log.info("MQTT conectado em {}, ouvindo {}", uri, topico);
        } catch (MqttException e) {
            log.warn("MQTT: falha ao assinar {}: {}", topico, e.getMessage());
        }
    }

    @Override
    public void messageArrived(String topicoRecebido, MqttMessage mensagem) {
        // Nenhuma exceção pode escapar daqui: o Paho derruba a conexão se o callback lançar.
        try {
            Long idosoId = Long.valueOf(topicoRecebido.substring(topicoRecebido.lastIndexOf('/') + 1));
            Double t = lerTemperatura(new String(mensagem.getPayload(), StandardCharsets.UTF_8));
            if (!CuidadoService.temperaturaValida(t)) {
                log.warn("MQTT: leitura descartada em {}: temperatura inválida", topicoRecebido);
                return;
            }
            var i = cuidado.leituraSensor(idosoId, t, "pelo MQTT");
            log.info("MQTT: sensor de {} leu {} °C, IRT agora {} ({})", i.getNome(), t, i.getIrt(), i.getNivel().getRotulo());
        } catch (Exception e) {
            log.warn("MQTT: leitura descartada em {}: {}", topicoRecebido, e.toString());
        }
    }

    /** {"temperatura": 36.0} vira 36.0; qualquer outra coisa vira null. */
    static Double lerTemperatura(String payload) {
        try {
            JsonNode n = JSON.readTree(payload).get("temperatura");
            return n != null && n.isNumber() ? n.asDouble() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void connectionLost(Throwable causa) {
        log.warn("MQTT: conexão perdida ({}). Reconectando...", causa.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void parar() {
        try {
            if (cliente != null && cliente.isConnected()) cliente.disconnect();
        } catch (MqttException ignorada) {
        }
    }
}
