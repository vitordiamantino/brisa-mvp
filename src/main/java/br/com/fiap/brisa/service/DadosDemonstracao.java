package br.com.fiap.brisa.service;

import br.com.fiap.brisa.model.Contato;
import br.com.fiap.brisa.model.Idoso;
import br.com.fiap.brisa.repository.CheckInRepository;
import br.com.fiap.brisa.repository.EventoRepository;
import br.com.fiap.brisa.repository.IdosoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Na primeira execução (tabelas vazias), cria a UBS de demonstração: Dona Lurdes e mais quatro idosos. Dados fictícios. */
@Component
public class DadosDemonstracao implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);

    private final IdosoRepository idosos;
    private final CheckInRepository checkIns;
    private final EventoRepository eventos;
    private final RiscoService risco;
    private final CuidadoService cuidado;

    public DadosDemonstracao(IdosoRepository idosos, CheckInRepository checkIns, EventoRepository eventos,
                             RiscoService risco, CuidadoService cuidado) {
        this.idosos = idosos;
        this.checkIns = checkIns;
        this.eventos = eventos;
        this.risco = risco;
        this.cuidado = cuidado;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (idosos.count() > 0) return;
        log.info("Banco vazio: criando os dados de demonstração da Brisa.");
        semear();
    }

    /** Apaga tudo e recria os dados de demonstração, mantendo os vínculos de Telegram de quem tem o mesmo nome. */
    @Transactional
    public void reiniciar() {
        java.util.Map<String, Long> chats = new java.util.HashMap<>();
        for (Idoso i : idosos.findAllByOrderByNomeAsc()) {
            if (i.getTelegramChatId() != null) chats.put("i:" + i.getNome(), i.getTelegramChatId());
            for (Contato c : i.getContatos()) if (c.getTelegramChatId() != null) chats.put("c:" + c.getNome(), c.getTelegramChatId());
        }
        eventos.deleteAllInBatch();
        checkIns.deleteAllInBatch();
        idosos.deleteAll();
        idosos.flush();
        semear();
        for (Idoso i : idosos.findAllByOrderByNomeAsc()) {
            i.setTelegramChatId(chats.get("i:" + i.getNome()));
            for (Contato c : i.getContatos()) c.setTelegramChatId(chats.get("c:" + c.getNome()));
        }
    }

    private void semear() {
        criar("Dona Lurdes", 78, "08210-000", "Itaquera", -23.5394, -46.4566, 33.0,
                true, true, false, false, true,
                new Contato("Carla (filha)", "Família", 1),
                new Contato("Seu Jorge (vizinho)", "Vizinho guardião", 2),
                new Contato("Márcia (agente da UBS)", "Agente de saúde", 3));
        criar("Seu Antônio", 84, "08220-000", "Itaquera", -23.5450, -46.4620, 34.5,
                true, false, true, false, true,
                new Contato("Rogério (filho)", "Família", 1),
                new Contato("Dona Neusa (vizinha)", "Vizinho guardião", 2),
                new Contato("Márcia (agente da UBS)", "Agente de saúde", 3));
        criar("Dona Cida", 81, "08230-000", "Itaquera", -23.5330, -46.4500, 31.0,
                true, true, false, false, false,
                new Contato("Paula (neta)", "Família", 1),
                new Contato("Márcia (agente da UBS)", "Agente de saúde", 2));
        criar("Dona Maria", 71, "08240-000", "Itaquera", -23.5410, -46.4700, 29.0,
                false, false, false, true, false,
                new Contato("Lucas (neto)", "Família", 1),
                new Contato("Márcia (agente da UBS)", "Agente de saúde", 2));
        criar("Seu José", 66, "08250-000", "Itaquera", -23.5480, -46.4480, 27.0,
                false, false, false, false, false,
                new Contato("Ana (filha)", "Família", 1));
    }

    private void criar(String nome, int idade, String cep, String bairro, double lat, double lon, double casa,
                       boolean sozinho, boolean hipertenso, boolean cardiaco, boolean diabetico, boolean telhado,
                       Contato... rede) {
        Idoso i = new Idoso();
        i.setNome(nome);
        i.setIdade(idade);
        i.setCep(cep);
        i.setBairro(bairro);
        i.setLatitude(lat);
        i.setLongitude(lon);
        i.setTemperaturaCasa(casa);
        i.setLeituraCasaEm(LocalDateTime.now());
        i.setMoraSozinho(sozinho);
        i.setHipertenso(hipertenso);
        i.setCardiaco(cardiaco);
        i.setDiabetico(diabetico);
        i.setTelhadoSemForro(telhado);
        i.setConsentimentoLgpd(true);
        for (Contato c : rede) {
            c.setIdoso(i);
            i.getContatos().add(c);
        }
        risco.calcular(i);
        idosos.save(i);
        cuidado.registrar(i, "CADASTRO", "Cadastrado pela UBS com consentimento LGPD e rede de " + rede.length + " contato(s).");
    }
}
