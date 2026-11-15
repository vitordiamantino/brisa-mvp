package br.com.fiap.brisa.service;

import br.com.fiap.brisa.model.*;
import br.com.fiap.brisa.repository.CheckInRepository;
import br.com.fiap.brisa.repository.EventoRepository;
import br.com.fiap.brisa.repository.IdosoRepository;
import br.com.fiap.brisa.service.TelegramService.Botao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * O coração da Brisa: ciclo diário (risco + check-ins em fila de prioridade pelo IRT),
 * respostas do idoso e escalonamento pela rede de cuidado (lista ligada de contatos).
 */
@Service
public class CuidadoService {

    /** Protocolo, em minutos: lembrete aos 30; primeiro contato aos 60; depois, um contato a cada 30. */
    static final int MIN_LEMBRETE = 30, MIN_PRIMEIRO_CONTATO = 60, MIN_ENTRE_CONTATOS = 30;

    private final IdosoRepository idosos;
    private final CheckInRepository checkIns;
    private final EventoRepository eventos;
    private final RiscoService risco;
    private final ClimaService clima;
    private final TelegramService telegram;
    private final int segundosPorMinuto;

    public CuidadoService(IdosoRepository idosos, CheckInRepository checkIns, EventoRepository eventos,
                          RiscoService risco, ClimaService clima, TelegramService telegram,
                          @Value("${brisa.demo.segundos-por-minuto:60}") int segundosPorMinuto) {
        this.idosos = idosos;
        this.checkIns = checkIns;
        this.eventos = eventos;
        this.risco = risco;
        this.clima = clima;
        this.telegram = telegram;
        this.segundosPorMinuto = Math.max(1, segundosPorMinuto);
    }

    // ---------- ciclo diário ----------

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void cicloDas6h() {
        executarCiclo();
    }

    /** Recalcula o IRT de todos e envia check-in a quem está em laranja ou vermelho, do maior risco para o menor. */
    @Transactional
    public int executarCiclo() {
        clima.limparCache();
        PriorityQueue<Idoso> fila = new PriorityQueue<>(Comparator.comparingDouble(Idoso::getIrt).reversed());
        for (Idoso i : idosos.findAll()) {
            risco.calcular(i);
            registrar(i, "RISCO", String.format(Locale.ROOT, "IRT %.1f (%s). Sensação prevista %.1f °C, casa %s.",
                    i.getIrt(), i.getNivel().getRotulo(), i.getSensacaoPrevista(), casaTexto(i)));
            if (i.getNivel() == Nivel.AMARELO) {
                enviarAoIdoso(i, "Brisa: hoje faz calor. Beba água e evite o sol das 10h às 16h.", List.of(), "DICA");
            }
            fila.add(i);
        }
        int enviados = 0;
        while (!fila.isEmpty()) {
            Idoso i = fila.poll();
            if (i.getNivel().recebeCheckIn()) {
                enviarCheckIn(i);
                enviados++;
            }
        }
        return enviados;
    }

    @Transactional
    public void recalcular(Long idosoId) {
        Idoso i = idosos.findById(idosoId).orElseThrow();
        risco.calcular(i);
        registrar(i, "RISCO", String.format(Locale.ROOT, "IRT recalculado: %.1f (%s).", i.getIrt(), i.getNivel().getRotulo()));
    }

    // ---------- check-in ----------

    @Transactional
    public CheckIn enviarCheckIn(Long idosoId) {
        return enviarCheckIn(idosos.findById(idosoId).orElseThrow());
    }

    private CheckIn enviarCheckIn(Idoso i) {
        checkIns.findFirstByIdosoIdAndStatusOrderByEnviadoEmDesc(i.getId(), CheckIn.Status.AGUARDANDO)
                .ifPresent(antigo -> { antigo.setStatus(CheckIn.Status.CONFIRMADO_POR_CONTATO); antigo.setResolvidoPor("substituído por novo check-in"); });
        CheckIn c = new CheckIn();
        c.setIdoso(i);
        c.setEnviadoEm(LocalDateTime.now());
        c.setIrtNoEnvio(i.getIrt());
        checkIns.save(c);
        String texto = "Oi, " + primeiroNome(i) + "! Hoje está fazendo muito calor.\nVocê está bem?";
        enviarAoIdoso(i, texto, botoesIdoso(c.getId()), "CHECKIN");
        return c;
    }

    static List<Botao> botoesIdoso(Long checkInId) {
        return List.of(new Botao("Estou bem", "bem:" + checkInId), new Botao("Preciso de ajuda", "ajuda:" + checkInId));
    }

    @Transactional
    public String responder(Long checkInId, boolean estouBem, String origem) {
        CheckIn c = checkIns.findById(checkInId).orElseThrow();
        if (!c.aberto()) return "Esse check-in já foi resolvido.";
        Idoso i = c.getIdoso();
        c.setRespondidoEm(LocalDateTime.now());
        if (estouBem) {
            c.setStatus(CheckIn.Status.ESTOU_BEM);
            c.setResolvidoPor(i.getNome());
            registrar(i, "RESPOSTA", "Respondeu Estou bem" + origem + ".");
            avisarRede(i, primeiroNome(i) + " respondeu que está bem.", false);
            return "Obrigado! Beba água e fique na sombra.";
        }
        c.setStatus(CheckIn.Status.PEDIU_AJUDA);
        c.setResolvidoPor("pedido de ajuda");
        registrar(i, "AJUDA", "Apertou Preciso de ajuda" + origem + ". Toda a rede foi avisada.");
        avisarRede(i, "URGENTE: " + i.getNome() + " pediu ajuda. Vá até lá ou ligue 192 (SAMU).", true);
        return "Avisamos sua família e a UBS. Se piorar, ligue 192.";
    }

    @Transactional
    public String confirmarPorContato(Long checkInId, Long contatoChatId, String nomeContato) {
        CheckIn c = checkIns.findById(checkInId).orElseThrow();
        if (!c.aberto()) return "Obrigado! Alguém já confirmou.";
        c.setStatus(CheckIn.Status.CONFIRMADO_POR_CONTATO);
        c.setRespondidoEm(LocalDateTime.now());
        c.setResolvidoPor(nomeContato);
        registrar(c.getIdoso(), "CONFIRMADO", nomeContato + " confirmou que falou com " + primeiroNome(c.getIdoso()) + ".");
        return "Obrigado por cuidar de " + primeiroNome(c.getIdoso()) + "!";
    }

    // ---------- escalonamento ----------

    /** Roda a cada 5 s; o relógio do protocolo é acelerado na demonstração (brisa.demo.segundos-por-minuto). */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void escalonar() {
        for (CheckIn c : checkIns.findByStatus(CheckIn.Status.AGUARDANDO)) {
            long minutos = Duration.between(c.getEnviadoEm(), LocalDateTime.now()).getSeconds() / segundosPorMinuto;
            Idoso i = c.getIdoso();
            if (c.getEtapa() == 0 && minutos >= MIN_LEMBRETE) {
                c.setEtapa(1);
                enviarAoIdoso(i, "Lembrete: " + primeiroNome(i) + ", você está bem?", botoesIdoso(c.getId()), "LEMBRETE");
                continue;
            }
            int proximoContato = c.getEtapa() - 1; // etapa 1 -> contato 0, etapa 2 -> contato 1...
            if (c.getEtapa() >= 1 && minutos >= MIN_PRIMEIRO_CONTATO + (long) MIN_ENTRE_CONTATOS * proximoContato) {
                LinkedList<Contato> rede = new LinkedList<>(i.getContatos());
                if (proximoContato < rede.size()) {
                    Contato contato = rede.get(proximoContato);
                    c.setEtapa(c.getEtapa() + 1);
                    String msg = primeiroNome(i) + " não respondeu ao check-in de calor (IRT "
                            + Math.round(c.getIrtNoEnvio() == null ? 0 : c.getIrtNoEnvio()) + ").\nPode falar com " + primeiroNome(i) + " agora?";
                    boolean foi = telegram.enviar(contato.getTelegramChatId(), msg,
                            List.of(new Botao("Já falei, está tudo bem","conf:" + c.getId())));
                    registrar(i, "ESCALONOU", "Sem resposta: avisou " + contato.getNome() + " (" + contato.getPapel() + ")"
                            + (foi ? " pelo Telegram." : " (sem Telegram vinculado: aviso simulado)."));
                } else if (c.getEtapa() == rede.size() + 1) {
                    c.setEtapa(c.getEtapa() + 1);
                    registrar(i, "ESCALONOU", "Toda a rede foi acionada sem confirmação: prioridade máxima de visita da UBS.");
                }
            }
        }
    }

    // ---------- apoio ----------

    private void avisarRede(Idoso i, String texto, boolean todos) {
        for (Contato ct : i.getContatos()) {
            if (!todos && !"Família".equalsIgnoreCase(ct.getPapel())) continue;
            boolean foi = telegram.enviar(ct.getTelegramChatId(), texto, List.of());
            if (todos) {
                registrar(i, "AVISO", "Avisou " + ct.getNome() + (foi ? " pelo Telegram." : " (aviso simulado)."));
            }
        }
    }

    private void enviarAoIdoso(Idoso i, String texto, List<Botao> botoes, String tipo) {
        boolean foi = telegram.enviar(i.getTelegramChatId(), texto, botoes);
        registrar(i, tipo, texto.replace('\n', ' ') + (foi ? "" : " (sem Telegram vinculado: envio simulado)"));
    }

    public void registrar(Idoso i, String tipo, String descricao) {
        eventos.save(new Evento(i, tipo, descricao));
    }

    static String primeiroNome(Idoso i) {
        String n = i.getNome().trim();
        return n.startsWith("Dona ") || n.startsWith("Seu ") ? n.split(" ")[0] + " " + n.split(" ")[1] : n.split(" ")[0];
    }

    private static String casaTexto(Idoso i) {
        return i.getTemperaturaCasa() == null ? "sem leitura" : String.format(Locale.ROOT, "%.1f °C", i.getTemperaturaCasa());
    }
}
