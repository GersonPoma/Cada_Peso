package com.presupuesto.cuenta.repository;

import com.presupuesto.cuenta.entity.Cuenta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByIdAndPresupuestoId(Long id, Long presupuestoId);

    List<Cuenta> findByPresupuestoIdOrderByNombreNormalizado(Long presupuestoId);

    List<Cuenta> findByPresupuestoIdAndCerradaFalseOrderByNombreNormalizado(Long presupuestoId);

    boolean existsByPresupuestoIdAndNombreNormalizado(Long presupuestoId, String nombreNormalizado);

    boolean existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
            Long presupuestoId, String nombreNormalizado, Long id);
}
