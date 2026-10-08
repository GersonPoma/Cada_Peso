package com.presupuesto.cuenta.repository;

import com.presupuesto.cuenta.entity.Cuenta;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByIdAndPresupuestoId(Long id, Long presupuestoId);

    /**
     * La cuenta con un bloqueo de escritura hasta el fin de la transacción: serializa las
     * conciliaciones de una misma cuenta. Cada transacción bloquea una sola cuenta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id and c.presupuesto.id = :presupuestoId")
    Optional<Cuenta> findByIdAndPresupuestoIdParaActualizar(
            @Param("id") Long id, @Param("presupuestoId") Long presupuestoId);

    List<Cuenta> findByPresupuestoIdOrderByNombreNormalizado(Long presupuestoId);

    List<Cuenta> findByPresupuestoIdAndCerradaFalseOrderByNombreNormalizado(Long presupuestoId);

    boolean existsByPresupuestoIdAndNombreNormalizado(Long presupuestoId, String nombreNormalizado);

    boolean existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
            Long presupuestoId, String nombreNormalizado, Long id);
}
