package br.com.fiap.brisa.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Linha do tempo do idoso: tudo o que o sistema fez e recebeu. */
@Entity
@Table(name = "BRISA_EVENTO")
@Getter
@Setter
@NoArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "IDOSO_ID", nullable = false)
    private Idoso idoso;

    @Column(nullable = false)
    private LocalDateTime quando;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 300)
    private String descricao;

    public Evento(Idoso idoso, String tipo, String descricao) {
        this.idoso = idoso;
        this.tipo = tipo;
        this.descricao = descricao.length() > 300 ? descricao.substring(0, 300) : descricao;
        this.quando = LocalDateTime.now();
    }
}
