package br.com.fiap.brisa.service;

import br.com.fiap.brisa.model.Idoso;
import br.com.fiap.brisa.model.Nivel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiscoServiceTest {

    @Test
    void exemploDaDonaLurdesDoSlideDaLaranja73() {
        Idoso lurdes = new Idoso();
        lurdes.setIdade(78);
        lurdes.setHipertenso(true);
        lurdes.setMoraSozinho(true);
        lurdes.setTelhadoSemForro(true);
        assertEquals(75, RiscoService.vulnerabilidade(lurdes));
        double irt = RiscoService.irt(79, 88, 75, 10);
        assertEquals(73.4, irt, 0.1);
        assertEquals(Nivel.LARANJA, Nivel.de(irt));
    }

    @Test
    void lurdesDoVideoRecemCadastradaDa73() {
        // Onda simulada de 37,5 °C, casa a 32,8 °C, sem histórico porque acabou de entrar.
        double c = RiscoService.calor(37.5), t = RiscoService.casa(32.8);
        assertEquals(81, c, 0.5);
        assertEquals(88, t, 0.01);
        double irt = RiscoService.irt(c, t, 75, RiscoService.historico(0));
        assertEquals(73.1, irt, 0.01);
        assertEquals(Nivel.LARANJA, Nivel.de(irt));
    }

    @Test
    void payloadDoSensorMqtt() {
        assertEquals(36.0, SensorMqtt.lerTemperatura("{\"temperatura\": 36.0}"));
        assertNull(SensorMqtt.lerTemperatura("{\"temp\": 36}"));
        assertNull(SensorMqtt.lerTemperatura("{\"temperatura\": \"36\"}"));
        assertNull(SensorMqtt.lerTemperatura("não é json"));
    }

    @Test
    void faixasDoSemaforo() {
        assertEquals(Nivel.VERDE, Nivel.de(39.9));
        assertEquals(Nivel.AMARELO, Nivel.de(40));
        assertEquals(Nivel.LARANJA, Nivel.de(60));
        assertEquals(Nivel.VERMELHO, Nivel.de(80));
    }

    @Test
    void componentesFicamEntreZeroECem() {
        assertEquals(0, RiscoService.calor(20));
        assertEquals(100, RiscoService.calor(45));
        assertEquals(90, RiscoService.casa(33.0), 0.01);
        assertEquals(100, RiscoService.historico(25));
    }
}
