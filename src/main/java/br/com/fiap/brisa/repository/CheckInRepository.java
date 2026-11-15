package br.com.fiap.brisa.repository;

import br.com.fiap.brisa.model.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    List<CheckIn> findByStatus(CheckIn.Status status);

    Optional<CheckIn> findFirstByIdosoIdOrderByEnviadoEmDesc(Long idosoId);

    Optional<CheckIn> findFirstByIdosoIdAndStatusOrderByEnviadoEmDesc(Long idosoId, CheckIn.Status status);

    long countByIdosoIdAndEnviadoEmAfterAndEtapaGreaterThan(Long idosoId, LocalDateTime desde, int etapa);

    List<CheckIn> findByEnviadoEmAfter(LocalDateTime desde);
}
