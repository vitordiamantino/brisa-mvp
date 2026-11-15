package br.com.fiap.brisa.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "BRISA_CHECKIN")
@Getter
@Setter
@NoArgsConstructor
public class CheckIn {

    public enum Status { AGUARDANDO, ESTOU_BEM, PEDIU_AJUDA, CONFIRMADO_POR_CONTATO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "IDOSO_ID", nullable = false)
    private Idoso idoso;

    @Column(nullable = false)
    private LocalDateTime enviadoEm;

    private LocalDateTime respondidoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Status status = Status.AGUARDANDO;

    private Double irtNoEnvio;

    /** Quantas etapas do escalonamento já foram executadas (0 = nenhuma). */
    private int etapa;

    @Column(length = 80)
    private String resolvidoPor;

    public boolean aberto() {
        return status == Status.AGUARDANDO;
    }

    public long minutosDeResposta() {
        return respondidoEm == null ? -1 : Duration.between(enviadoEm, respondidoEm).toMinutes();
    }
}
