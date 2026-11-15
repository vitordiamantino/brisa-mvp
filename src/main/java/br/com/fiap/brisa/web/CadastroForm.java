package br.com.fiap.brisa.web;

import br.com.fiap.brisa.model.Contato;
import br.com.fiap.brisa.model.Idoso;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Formulário de cadastro do idoso com até três contatos, em ordem de acionamento. */
@Getter
@Setter
public class CadastroForm {

    private String nome;
    private Integer idade;
    private String cep;
    private boolean moraSozinho = true;
    private boolean hipertenso;
    private boolean cardiaco;
    private boolean diabetico;
    private boolean telhadoSemForro;
    private boolean consentimentoLgpd;
    private Double temperaturaCasa;

    private String contato1Nome, contato1Papel = "Família";
    private String contato2Nome, contato2Papel = "Vizinho guardião";
    private String contato3Nome, contato3Papel = "Agente de saúde";

    public List<String> validar() {
        List<String> erros = new ArrayList<>();
        if (nome == null || nome.isBlank()) erros.add("Informe o nome.");
        if (idade == null || idade < 60 || idade > 120) erros.add("A Brisa acompanha pessoas com 60 anos ou mais.");
        if (cep == null || cep.replaceAll("\\D", "").length() != 8) erros.add("Informe um CEP com 8 dígitos.");
        if (contato1Nome == null || contato1Nome.isBlank()) erros.add("A rede de cuidado precisa de pelo menos um contato.");
        if (!consentimentoLgpd) erros.add("Sem o consentimento LGPD o cadastro não pode ser feito.");
        return erros;
    }

    public Idoso paraIdoso() {
        Idoso i = new Idoso();
        i.setNome(nome.trim());
        i.setIdade(idade);
        i.setCep(cep.replaceAll("\\D", "").replaceFirst("(\\d{5})(\\d{3})", "$1-$2"));
        i.setMoraSozinho(moraSozinho);
        i.setHipertenso(hipertenso);
        i.setCardiaco(cardiaco);
        i.setDiabetico(diabetico);
        i.setTelhadoSemForro(telhadoSemForro);
        i.setConsentimentoLgpd(true);
        i.setTemperaturaCasa(temperaturaCasa);
        int ordem = 1;
        for (String[] c : new String[][]{{contato1Nome, contato1Papel}, {contato2Nome, contato2Papel}, {contato3Nome, contato3Papel}}) {
            if (c[0] != null && !c[0].isBlank()) {
                Contato ct = new Contato(c[0].trim(), c[1], ordem++);
                ct.setIdoso(i);
                i.getContatos().add(ct);
            }
        }
        return i;
    }
}
