package com.presupuesto.presupuesto.repository;

import com.presupuesto.presupuesto.entity.Presupuesto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    Optional<Presupuesto> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Presupuesto> findByUsuarioIdOrderByNombreNormalizado(Long usuarioId);

    boolean existsByUsuarioIdAndNombreNormalizado(Long usuarioId, String nombreNormalizado);

    boolean existsByUsuarioIdAndNombreNormalizadoAndIdNot(
            Long usuarioId, String nombreNormalizado, Long id);
}
