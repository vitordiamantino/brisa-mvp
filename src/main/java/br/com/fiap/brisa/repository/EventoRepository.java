package br.com.fiap.brisa.repository;

import br.com.fiap.brisa.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findTop30ByIdosoIdOrderByQuandoDesc(Long idosoId);
}
