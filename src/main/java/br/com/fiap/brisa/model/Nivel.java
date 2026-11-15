package br.com.fiap.brisa.model;

public enum Nivel {
    VERDE("Verde", "Só uma dica opcional pela manhã"),
    AMARELO("Amarelo", "Dica às 9h: hidratação e horário de evitar o sol"),
    LARANJA("Laranja", "Check-ins às 10h, 14h e 17h; a família vê o status"),
    VERMELHO("Vermelho", "Check-in a cada 2h; família avisada; topo da lista da UBS");

    private final String rotulo;
    private final String protocolo;

    Nivel(String rotulo, String protocolo) {
        this.rotulo = rotulo;
        this.protocolo = protocolo;
    }

    public String getRotulo() { return rotulo; }
    public String getProtocolo() { return protocolo; }
    public String getCss() { return name().toLowerCase(); }

    public boolean recebeCheckIn() {
        return this == LARANJA || this == VERMELHO;
    }

    public static Nivel de(double irt) {
        if (irt >= 80) return VERMELHO;
        if (irt >= 60) return LARANJA;
        if (irt >= 40) return AMARELO;
        return VERDE;
    }
}
