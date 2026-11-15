package br.com.fiap.brisa.repository;

import br.com.fiap.brisa.model.Idoso;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdosoRepository extends JpaRepository<Idoso, Long> {

    @EntityGraph(attributePaths = "contatos")
    Optional<Idoso> findComContatosById(Long id);

    @EntityGraph(attributePaths = "contatos")
    List<Idoso> findAllByOrderByNomeAsc();

    Optional<Idoso> findByTelegramChatId(Long chatId);
}
