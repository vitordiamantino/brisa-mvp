package br.com.fiap.brisa.web;

import br.com.fiap.brisa.model.*;
import br.com.fiap.brisa.repository.CheckInRepository;
import br.com.fiap.brisa.repository.EventoRepository;
import br.com.fiap.brisa.repository.IdosoRepository;
import br.com.fiap.brisa.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class PainelController {

    private final IdosoRepository idosos;
    private final CheckInRepository checkIns;
    private final EventoRepository eventos;
    private final CuidadoService cuidado;
    private final RiscoService risco;
    private final ClimaService clima;
    private final TelegramService telegram;
    private final DadosDemonstracao demo;

    public PainelController(IdosoRepository idosos, CheckInRepository checkIns, EventoRepository eventos,
                            CuidadoService cuidado, RiscoService risco, ClimaService clima, TelegramService telegram,
                            DadosDemonstracao demo) {
        this.demo = demo;
        this.idosos = idosos;
        this.checkIns = checkIns;
        this.eventos = eventos;
        this.cuidado = cuidado;
        this.risco = risco;
        this.clima = clima;
        this.telegram = telegram;
    }

    /** Linha do painel: o idoso e o último check-in dele. */
    public record Linha(Idoso idoso, CheckIn ultimo) {
        public boolean semResposta() { return ultimo != null && ultimo.aberto(); }
        public boolean pediuAjuda() { return ultimo != null && ultimo.getStatus() == CheckIn.Status.PEDIU_AJUDA; }
    }

    @ModelAttribute
    public void comum(Model model) {
        model.addAttribute("telegramAtivo", telegram.ativo());
        model.addAttribute("bot", telegram.getUsuarioBot());
        model.addAttribute("previsaoReal", clima.getUltimaPrevisaoReal());
        model.addAttribute("ondaSimulada", clima.getOndaSimulada());
    }

    @GetMapping("/")
    public String painel(Model model) {
        List<Linha> linhas = new ArrayList<>();
        for (Idoso i : idosos.findAllByOrderByNomeAsc()) {
            linhas.add(new Linha(i, checkIns.findFirstByIdosoIdOrderByEnviadoEmDesc(i.getId()).orElse(null)));
        }
        // Quem pediu ajuda ou está sem resposta sobe para o topo; depois, maior IRT primeiro.
        linhas.sort(Comparator.comparing((Linha l) -> !l.pediuAjuda())
                .thenComparing(l -> !l.semResposta())
                .thenComparing(l -> -(l.idoso().getIrt() == null ? 0 : l.idoso().getIrt())));
        Map<Nivel, Long> contagem = new EnumMap<>(Nivel.class);
        for (Nivel n : Nivel.values()) contagem.put(n, 0L);
        linhas.forEach(l -> contagem.merge(l.idoso().getNivel(), 1L, Long::sum));

        List<CheckIn> hoje = checkIns.findByEnviadoEmAfter(LocalDateTime.now().toLocalDate().atStartOfDay());
        long respondidosEm30 = hoje.stream().filter(c -> c.getStatus() == CheckIn.Status.ESTOU_BEM
                && c.minutosDeResposta() >= 0 && c.minutosDeResposta() <= 30).count();

        model.addAttribute("linhas", linhas);
        model.addAttribute("contagem", contagem);
        model.addAttribute("checkinsHoje", hoje.size());
        model.addAttribute("respondidosEm30", respondidosEm30);
        model.addAttribute("semResposta", linhas.stream().filter(Linha::semResposta).count());
        return "painel";
    }

    @GetMapping("/idosos/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        Idoso i = idosos.findComContatosById(id).orElseThrow();
        model.addAttribute("i", i);
        model.addAttribute("ultimo", checkIns.findFirstByIdosoIdOrderByEnviadoEmDesc(id).orElse(null));
        model.addAttribute("eventos", eventos.findTop30ByIdosoIdOrderByQuandoDesc(id));
        return "idoso";
    }

    @GetMapping("/idosos/novo")
    public String novo(Model model) {
        model.addAttribute("form", new CadastroForm());
        return "cadastro";
    }

    @PostMapping("/idosos")
    @Transactional
    public String cadastrar(@ModelAttribute("form") CadastroForm f, Model model, RedirectAttributes ra) {
        List<String> erros = f.validar();
        if (!erros.isEmpty()) {
            model.addAttribute("erros", erros);
            return "cadastro";
        }
        Idoso i = f.paraIdoso();
        ClimaService.Coordenadas c = clima.coordenadasDoCep(f.getCep());
        i.setLatitude(c.latitude());
        i.setLongitude(c.longitude());
        if (c.bairro() != null) i.setBairro(c.bairro());
        risco.calcular(i);
        idosos.save(i);
        cuidado.registrar(i, "CADASTRO", "Cadastrado com consentimento LGPD e rede de " + i.getContatos().size() + " contato(s).");
        ra.addFlashAttribute("msg", i.getNome() + " cadastrado(a). IRT de hoje: " + i.getIrt() + " (" + i.getNivel().getRotulo() + ").");
        return "redirect:/idosos/" + i.getId();
    }

    @PostMapping("/ciclo")
    public String ciclo(RedirectAttributes ra) {
        int n = cuidado.executarCiclo();
        ra.addFlashAttribute("msg", "Ciclo das 6h executado: risco recalculado e " + n + " check-in(s) enviado(s), do maior IRT para o menor.");
        return "redirect:/";
    }

    @PostMapping("/demo/reiniciar")
    public String reiniciar(RedirectAttributes ra) {
        demo.reiniciar();
        clima.setOndaSimulada(null);
        ra.addFlashAttribute("msg", "Demonstração reiniciada: dados iniciais restaurados, vínculos do Telegram mantidos.");
        return "redirect:/";
    }

    @PostMapping("/onda")
    public String onda(@RequestParam(required = false) Double sensacao, RedirectAttributes ra) {
        clima.setOndaSimulada(sensacao);
        ra.addFlashAttribute("msg", sensacao == null ? "Simulação desligada: o sistema volta a usar a previsão real."
                : "Onda de calor simulada: sensação de " + sensacao + " °C. Rode o ciclo das 6h para recalcular.");
        return "redirect:/";
    }

    @PostMapping("/idosos/{id}/checkin")
    public String checkin(@PathVariable Long id, RedirectAttributes ra) {
        cuidado.enviarCheckIn(id);
        ra.addFlashAttribute("msg", "Check-in enviado.");
        return "redirect:/idosos/" + id;
    }

    @PostMapping("/idosos/{id}/responder")
    public String responder(@PathVariable Long id, @RequestParam Long checkIn, @RequestParam boolean bem, RedirectAttributes ra) {
        ra.addFlashAttribute("msg", cuidado.responder(checkIn, bem, " (simulado no painel)"));
        return "redirect:/idosos/" + id;
    }

    @PostMapping("/idosos/{id}/sensor")
    @Transactional
    public String sensor(@PathVariable Long id, @RequestParam Double temperatura, RedirectAttributes ra) {
        if (temperatura < -10 || temperatura > 60) {
            ra.addFlashAttribute("msg", "Leitura recusada: " + temperatura + " °C está fora da faixa de um sensor de casa (-10 a 60 °C).");
            return "redirect:/idosos/" + id;
        }
        Idoso i = idosos.findById(id).orElseThrow();
        i.setTemperaturaCasa(temperatura);
        i.setLeituraCasaEm(LocalDateTime.now());
        risco.calcular(i);
        cuidado.registrar(i, "SENSOR", "Leitura do sensor da casa: " + temperatura + " °C. IRT agora " + i.getIrt() + ".");
        ra.addFlashAttribute("msg", "Leitura registrada. IRT agora " + i.getIrt() + " (" + i.getNivel().getRotulo() + ").");
        return "redirect:/idosos/" + id;
    }

    @PostMapping("/idosos/{id}/recalcular")
    public String recalcular(@PathVariable Long id, RedirectAttributes ra) {
        cuidado.recalcular(id);
        ra.addFlashAttribute("msg", "IRT recalculado.");
        return "redirect:/idosos/" + id;
    }
}
