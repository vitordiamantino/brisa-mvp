package br.com.fiap.brisa.web;

import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Idoso ou check-in que não existe mais (por exemplo, uma aba aberta antes de "Recomeçar a demonstração")
 * volta ao painel com um aviso, em vez de mostrar a página de erro.
 */
@ControllerAdvice(assignableTypes = PainelController.class)
public class NaoEncontradoAdvice {

    @ExceptionHandler(NoSuchElementException.class)
    public String naoEncontrado(RedirectAttributes ra) {
        ra.addFlashAttribute("msg", "Esse registro não existe mais. Se a demonstração foi reiniciada, escolha o idoso de novo no painel.");
        return "redirect:/";
    }
}
