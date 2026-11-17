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

/** Na primeira execução (tabelas vazias), cria a UBS de demonstração com 14 idosos fictícios. */
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

    private static final String FAMILIA = "Família", VIZINHO = "Vizinho guardião", AGENTE = "Agente de saúde";

    /**
     * 14 idosos fictícios de Itaquera. A Dona Lurdes fica de fora: ela é cadastrada ao vivo no vídeo.
     * Com a onda simulada de 37,5 °C, ficam 2 em vermelho, 6 em laranja, 4 em amarelo e 2 em verde.
     */
    private void semear() {
        String ana = "Ana (agente da UBS)";
        criar("Seu Antônio", 84, "08220-000", -23.5450, -46.4620, 34.5, true, true, true, false, true,
                new Contato("Rogério (filho)", FAMILIA, 1), new Contato("Dona Neusa (vizinha)", VIZINHO, 2), new Contato(ana, AGENTE, 3));
        criar("Dona Benedita", 88, "08225-000", -23.5420, -46.4590, 34.0, true, true, true, false, true,
                new Contato("Sílvia (filha)", FAMILIA, 1), new Contato(ana, AGENTE, 2));
        criar("Seu Sebastião", 85, "08226-000", -23.5470, -46.4550, 33.0, true, true, false, true, true,
                new Contato("Márcio (filho)", FAMILIA, 1), new Contato("Seu Nelson (vizinho)", VIZINHO, 2), new Contato(ana, AGENTE, 3));
        criar("Dona Terezinha", 83, "08227-000", -23.5360, -46.4610, 33.5, true, false, true, false, false,
                new Contato("Cláudia (filha)", FAMILIA, 1), new Contato(ana, AGENTE, 2));
        criar("Seu Raimundo", 79, "08228-000", -23.5390, -46.4680, 32.0, true, true, false, true, false,
                new Contato("Fábio (neto)", FAMILIA, 1), new Contato(ana, AGENTE, 2));
        criar("Dona Cida", 81, "08230-000", -23.5330, -46.4500, 31.0, true, true, false, false, false,
                new Contato("Paula (neta)", FAMILIA, 1), new Contato(ana, AGENTE, 2));
        criar("Dona Zélia", 74, "08232-000", -23.5440, -46.4520, 31.5, true, false, false, false, true,
                new Contato("Renata (sobrinha)", FAMILIA, 1), new Contato("Seu Carlos (vizinho)", VIZINHO, 2), new Contato(ana, AGENTE, 3));
        criar("Dona Aparecida", 76, "08234-000", -23.5380, -46.4470, 30.5, true, true, false, false, false,
                new Contato("Juliana (filha)", FAMILIA, 1), new Contato(ana, AGENTE, 2));
        criar("Dona Maria", 71, "08240-000", -23.5410, -46.4700, 29.0, false, false, false, true, false,
                new Contato("Lucas (neto)", FAMILIA, 1), new Contato(ana, AGENTE, 2));
        criar("Seu Geraldo", 69, "08242-000", -23.5500, -46.4650, 30.0, false, true, false, false, false,
                new Contato("Patrícia (filha)", FAMILIA, 1));
        criar("Seu Osvaldo", 67, "08244-000", -23.5350, -46.4720, 28.0, false, false, false, false, false,
                new Contato("Diego (filho)", FAMILIA, 1));
        criar("Seu José", 66, "08250-000", -23.5480, -46.4480, 27.0, false, false, false, false, false,
                new Contato("Ana Paula (filha)", FAMILIA, 1));
        criar("Seu Valdir", 62, "08252-000", -23.5520, -46.4600, 26.0, false, false, false, false, false,
                new Contato("Bruna (filha)", FAMILIA, 1));
        criar("Dona Iracema", 64, "08254-000", -23.5310, -46.4640, 25.5, false, false, false, false, false,
                new Contato("Tiago (neto)", FAMILIA, 1));
    }

    private void criar(String nome, int idade, String cep, double lat, double lon, double casa,
                       boolean sozinho, boolean hipertenso, boolean cardiaco, boolean diabetico, boolean telhado,
                       Contato... rede) {
        Idoso i = new Idoso();
        i.setNome(nome);
        i.setIdade(idade);
        i.setCep(cep);
        i.setBairro("Itaquera");
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
