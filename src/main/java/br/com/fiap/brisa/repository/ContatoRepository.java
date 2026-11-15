package br.com.fiap.brisa.repository;

import br.com.fiap.brisa.model.Contato;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContatoRepository extends JpaRepository<Contato, Long> {

    @EntityGraph(attributePaths = "idoso")
    List<Contato> findByTelegramChatId(Long chatId);

    @EntityGraph(attributePaths = "idoso")
    Optional<Contato> findComIdosoById(Long id);
}
