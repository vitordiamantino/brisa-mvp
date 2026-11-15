package br.com.fiap.brisa.service;

import br.com.fiap.brisa.model.Idoso;
import br.com.fiap.brisa.model.Nivel;
import br.com.fiap.brisa.repository.CheckInRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Motor de risco: IRT = 0,40·C + 0,25·T_int + 0,25·V + 0,10·H, cada componente de 0 a 100.
 * Pesos iniciais da equipe; o IRT organiza o cuidado e não faz diagnóstico.
 */
@Service
public class RiscoService {

    private final ClimaService clima;
    private final CheckInRepository checkIns;

    public RiscoService(ClimaService clima, CheckInRepository checkIns) {
        this.clima = clima;
        this.checkIns = checkIns;
    }

    /** Sensação térmica de 27 °C vale 0 e de 40 °C vale 100. */
    public static double calor(double sensacao) {
        return escala(sensacao, 27, 40);
    }

    /** Temperatura dentro de casa: 24 °C vale 0 e 34 °C vale 100. */
    public static double casa(Double temperatura) {
        return temperatura == null ? 50 : escala(temperatura, 24, 34);
    }

    public static double vulnerabilidade(Idoso i) {
        int idade = i.getIdade() == null ? 0 : i.getIdade();
        double v = idade >= 80 ? 30 : idade >= 70 ? 20 : idade >= 60 ? 10 : 0;
        if (i.isHipertenso()) v += 15;
        if (i.isCardiaco()) v += 15;
        if (i.isDiabetico()) v += 10;
        if (i.isMoraSozinho()) v += 20;
        if (i.isTelhadoSemForro()) v += 20;
        return Math.min(100, v);
    }

    /** Cada check-in que precisou de escalonamento nos últimos 7 dias soma 10. */
    public static double historico(long escalonamentos) {
        return Math.min(100, escalonamentos * 10.0);
    }

    public static double irt(double c, double tInt, double v, double h) {
        return Math.round((0.40 * c + 0.25 * tInt + 0.25 * v + 0.10 * h) * 10) / 10.0;
    }

    public void calcular(Idoso i) {
        double sensacao = clima.sensacaoMaximaHoje(i.getLatitude(), i.getLongitude());
        double c = calor(sensacao);
        double t = casa(i.getTemperaturaCasa());
        double v = vulnerabilidade(i);
        long esc = i.getId() == null ? 0
                : checkIns.countByIdosoIdAndEnviadoEmAfterAndEtapaGreaterThan(i.getId(), LocalDateTime.now().minusDays(7), 0);
        double h = historico(esc);
        double valor = irt(c, t, v, h);
        i.setSensacaoPrevista(sensacao);
        i.setComponenteCalor(arred(c));
        i.setComponenteCasa(arred(t));
        i.setComponenteVulnerabilidade(arred(v));
        i.setComponenteHistorico(arred(h));
        i.setIrt(valor);
        i.setNivel(Nivel.de(valor));
        i.setRiscoCalculadoEm(LocalDateTime.now());
    }

    private static double escala(double valor, double min, double max) {
        return Math.max(0, Math.min(100, (valor - min) / (max - min) * 100));
    }

    private static double arred(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
