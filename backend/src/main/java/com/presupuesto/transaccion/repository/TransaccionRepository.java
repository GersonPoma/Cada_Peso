package com.presupuesto.transaccion.repository;

import com.presupuesto.transaccion.entity.Transaccion;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionRepository
        extends JpaRepository<Transaccion, Long>, JpaSpecificationExecutor<Transaccion> {

    Optional<Transaccion> findByIdAndCuentaPresupuestoId(Long id, Long presupuestoId);

    boolean existsByProgramadaIdAndFechaOcurrencia(Long programadaId, LocalDate fechaOcurrencia);

    /** Deja las transacciones generadas por una plantilla sin vínculo (al borrarla). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transaccion t
            set t.programadaId = null, t.fechaOcurrencia = null
            where t.programadaId = :programadaId
            """)
    int desvincularProgramada(@Param("programadaId") Long programadaId);

    List<Transaccion> findByIdInAndCuentaPresupuestoId(
            Collection<Long> ids, Long presupuestoId);

    /**
     * Suma de las transacciones por cuenta del presupuesto: total y conciliado (estados
     * {@code CONCILIADA} y {@code RECONCILIADA}). Las cuentas sin transacciones no aparecen.
     */
    @Query("""
            select t.cuenta.id as cuentaId,
                   sum(t.monto) as total,
                   sum(case when t.estado <> com.presupuesto.transaccion.entity
                                .EstadoTransaccion.NO_CONCILIADA
                            then t.monto else 0L end) as conciliado
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
            group by t.cuenta.id
            """)
    List<SumaPorCuenta> sumarPorCuenta(@Param("presupuestoId") Long presupuestoId);

    interface SumaPorCuenta {

        Long getCuentaId();

        Long getTotal();

        Long getConciliado();
    }
}
