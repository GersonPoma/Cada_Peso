package com.presupuesto.presupuesto.repository;

import com.presupuesto.presupuesto.entity.Presupuesto;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    Optional<Presupuesto> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Presupuesto> findByUsuarioIdOrderByNombreNormalizado(Long usuarioId);

    boolean existsByUsuarioIdAndNombreNormalizado(Long usuarioId, String nombreNormalizado);

    boolean existsByUsuarioIdAndNombreNormalizadoAndIdNot(
            Long usuarioId, String nombreNormalizado, Long id);

    /**
     * El presupuesto con un bloqueo de escritura hasta el fin de la transacción: serializa a
     * quienes van a crear el grupo de pagos de tarjetas. Cada transacción bloquea un solo
     * presupuesto, nunca dos, para evitar interbloqueos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Presupuesto p where p.id = :id")
    Optional<Presupuesto> findByIdParaActualizar(@Param("id") Long id);
}
