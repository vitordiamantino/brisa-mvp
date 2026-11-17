package br.com.fiap.brisa.web;

import br.com.fiap.brisa.model.Idoso;
import br.com.fiap.brisa.repository.IdosoRepository;
import br.com.fiap.brisa.service.CuidadoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** API REST: dados do painel e entrada das leituras do sensor de casa (o ESP32 + DHT22 também chega por MQTT, veja SensorMqtt). */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final IdosoRepository idosos;
    private final CuidadoService cuidado;

    public ApiController(IdosoRepository idosos, CuidadoService cuidado) {
        this.idosos = idosos;
        this.cuidado = cuidado;
    }

    public record RiscoIdoso(Long id, String nome, String bairro, Double irt, String nivel, Double sensacaoPrevista, Double temperaturaCasa) {}

    @GetMapping("/idosos")
    public List<RiscoIdoso> listar() {
        return idosos.findAll().stream()
                .sorted(Comparator.comparing((Idoso i) -> i.getIrt() == null ? 0 : i.getIrt()).reversed())
                .map(i -> new RiscoIdoso(i.getId(), i.getNome(), i.getBairro(), i.getIrt(), i.getNivel().name(),
                        i.getSensacaoPrevista(), i.getTemperaturaCasa()))
                .toList();
    }

    public record LeituraSensor(Double temperatura) {}

    /** Exemplo: POST /api/sensor/1 com {"temperatura": 33.4} */
    @PostMapping("/sensor/{idosoId}")
    public ResponseEntity<Map<String, Object>> sensor(@PathVariable Long idosoId, @RequestBody LeituraSensor leitura) {
        if (leitura == null || !CuidadoService.temperaturaValida(leitura.temperatura())) {
            return ResponseEntity.badRequest().body(Map.of("erro", "temperatura inválida"));
        }
        if (!idosos.existsById(idosoId)) return ResponseEntity.notFound().build();
        Idoso i = cuidado.leituraSensor(idosoId, leitura.temperatura(), "pela API");
        return ResponseEntity.ok(Map.of("idoso", i.getNome(), "irt", i.getIrt(), "nivel", i.getNivel().name()));
    }
}
