package br.com.fiap.brisa.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "BRISA_IDOSO")
@Getter
@Setter
@NoArgsConstructor
public class Idoso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    private Integer idade;

    @Column(length = 9)
    private String cep;

    @Column(length = 60)
    private String bairro;

    private Double latitude;
    private Double longitude;

    private boolean moraSozinho;
    private boolean hipertenso;
    private boolean cardiaco;
    private boolean diabetico;
    private boolean telhadoSemForro;
    private boolean consentimentoLgpd;

    private Long telegramChatId;

    /** Leitura mais recente do sensor de casa (simulado no MVP). */
    private Double temperaturaCasa;
    private LocalDateTime leituraCasaEm;

    private Double sensacaoPrevista;
    private Double irt;
    private Double componenteCalor;
    private Double componenteCasa;
    private Double componenteVulnerabilidade;
    private Double componenteHistorico;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Nivel nivel = Nivel.VERDE;

    private LocalDateTime riscoCalculadoEm;

    @OneToMany(mappedBy = "idoso", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<Contato> contatos = new ArrayList<>();
}
