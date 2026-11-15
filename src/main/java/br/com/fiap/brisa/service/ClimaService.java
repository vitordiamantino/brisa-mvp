package br.com.fiap.brisa.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coletor de clima: CEP para coordenadas (BrasilAPI) e sensação térmica máxima do dia (Open-Meteo).
 * Para a demonstração, uma "onda de calor simulada" pode substituir a previsão real.
 */
@Service
public class ClimaService {

    private static final Logger log = LoggerFactory.getLogger(ClimaService.class);
    private static final double SP_LAT = -23.5505, SP_LON = -46.6333;
    private static final double FALLBACK_SENSACAO = 33.0;

    private final RestClient http = RestClient.create();
    private final Map<String, Double> cachePrevisao = new ConcurrentHashMap<>();
    private volatile Double ondaSimulada;
    private volatile Double ultimaPrevisaoReal;

    public record Coordenadas(double latitude, double longitude, String bairro) {}

    public Coordenadas coordenadasDoCep(String cep) {
        String limpo = cep == null ? "" : cep.replaceAll("\\D", "");
        if (limpo.length() != 8) return new Coordenadas(SP_LAT, SP_LON, null);
        try {
            JsonNode r = http.get().uri("https://brasilapi.com.br/api/cep/v2/{cep}", limpo)
                    .retrieve().body(JsonNode.class);
            String bairro = r.path("neighborhood").asText(null);
            JsonNode c = r.path("location").path("coordinates");
            String lat = c.path("latitude").asText(""), lon = c.path("longitude").asText("");
            if (!lat.isBlank() && !lon.isBlank()) {
                return new Coordenadas(Double.parseDouble(lat), Double.parseDouble(lon), bairro);
            }
            return new Coordenadas(SP_LAT, SP_LON, bairro);
        } catch (Exception e) {
            log.warn("BrasilAPI indisponível para o CEP {}: {}", limpo, e.getMessage());
            return new Coordenadas(SP_LAT, SP_LON, null);
        }
    }

    /** Sensação térmica máxima prevista para hoje, em °C. */
    public double sensacaoMaximaHoje(Double latitude, Double longitude) {
        double real = previsaoReal(latitude == null ? SP_LAT : latitude, longitude == null ? SP_LON : longitude);
        return ondaSimulada != null ? ondaSimulada : real;
    }

    private double previsaoReal(double lat, double lon) {
        String chave = String.format(java.util.Locale.ROOT, "%.2f,%.2f", lat, lon);
        Double emCache = cachePrevisao.get(chave);
        if (emCache != null) return emCache;
        try {
            JsonNode r = http.get().uri(
                    "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                            + "&daily=apparent_temperature_max&timezone={tz}&forecast_days=1",
                    lat, lon, "America/Sao_Paulo").retrieve().body(JsonNode.class);
            double v = r.path("daily").path("apparent_temperature_max").path(0).asDouble(FALLBACK_SENSACAO);
            ultimaPrevisaoReal = v;
            cachePrevisao.put(chave, v);
            return v;
        } catch (Exception e) {
            log.warn("Open-Meteo indisponível, usando {} °C: {}", FALLBACK_SENSACAO, e.getMessage());
            return FALLBACK_SENSACAO;
        }
    }

    /** Chamado no ciclo diário para buscar a previsão nova. */
    public void limparCache() {
        cachePrevisao.clear();
    }

    public Double getOndaSimulada() { return ondaSimulada; }
    public void setOndaSimulada(Double valor) { this.ondaSimulada = valor; }
    public Double getUltimaPrevisaoReal() { return ultimaPrevisaoReal; }
}
