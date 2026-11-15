package br.com.fiap.brisa.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "BRISA_CONTATO")
@Getter
@Setter
@NoArgsConstructor
public class Contato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    /** Família, Vizinho guardião ou Agente de saúde (UBS). */
    @Column(nullable = false, length = 30)
    private String papel;

    /** Ordem de acionamento na rede de cuidado: 1 é o primeiro. */
    @Column(nullable = false)
    private Integer ordem;

    private Long telegramChatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "IDOSO_ID", nullable = false)
    private Idoso idoso;

    public Contato(String nome, String papel, int ordem) {
        this.nome = nome;
        this.papel = papel;
        this.ordem = ordem;
    }
}
