package br.com.fiap.brisa.web;

import br.com.fiap.brisa.model.Idoso;
import br.com.fiap.brisa.repository.IdosoRepository;
import br.com.fiap.brisa.service.CuidadoService;
import br.com.fiap.brisa.service.RiscoService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** API REST: dados do painel e entrada das leituras do sensor de casa (ESP32 + DHT22 no roadmap). */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final IdosoRepository idosos;
    private final RiscoService risco;
    private final CuidadoService cuidado;

    public ApiController(IdosoRepository idosos, RiscoService risco, CuidadoService cuidado) {
        this.idosos = idosos;
        this.risco = risco;
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
    @Transactional
    public ResponseEntity<Map<String, Object>> sensor(@PathVariable Long idosoId, @RequestBody LeituraSensor leitura) {
        if (leitura == null || leitura.temperatura() == null || leitura.temperatura() < -10 || leitura.temperatura() > 60) {
            return ResponseEntity.badRequest().body(Map.of("erro", "temperatura inválida"));
        }
        Idoso i = idosos.findById(idosoId).orElse(null);
        if (i == null) return ResponseEntity.notFound().build();
        i.setTemperaturaCasa(leitura.temperatura());
        i.setLeituraCasaEm(LocalDateTime.now());
        risco.calcular(i);
        cuidado.registrar(i, "SENSOR", "Sensor da casa enviou " + leitura.temperatura() + " °C pela API. IRT agora " + i.getIrt() + ".");
        return ResponseEntity.ok(Map.of("idoso", i.getNome(), "irt", i.getIrt(), "nivel", i.getNivel().name()));
    }
}
