package com.presupuesto.transaccion.repository;

import com.presupuesto.transaccion.entity.Transaccion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /**
     * Suma de la cuenta con fecha menor o igual a {@code hasta} y estado {@code CONCILIADA} o
     * {@code RECONCILIADA} (mismo criterio que {@link #sumarPorCuenta}); 0 si no hay ninguna.
     */
    @Query("""
            select coalesce(sum(t.monto), 0L)
            from Transaccion t
            where t.cuenta.id = :cuentaId
              and t.estado <> com.presupuesto.transaccion.entity.EstadoTransaccion.NO_CONCILIADA
              and t.fecha <= :hasta
            """)
    long sumaConciliadaDeCuenta(
            @Param("cuentaId") Long cuentaId, @Param("hasta") LocalDate hasta);

    /**
     * Pasa a {@code RECONCILIADA} las {@code CONCILIADA} de la cuenta con fecha menor o igual a
     * {@code hasta}. El {@code UPDATE} no dispara {@code @UpdateTimestamp}: se fija a mano.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transaccion t
            set t.estado = com.presupuesto.transaccion.entity.EstadoTransaccion.RECONCILIADA,
                t.fechaActualizacion = :ahora
            where t.cuenta.id = :cuentaId
              and t.estado = com.presupuesto.transaccion.entity.EstadoTransaccion.CONCILIADA
              and t.fecha <= :hasta
            """)
    int reconciliarConciliadasHasta(
            @Param("cuentaId") Long cuentaId,
            @Param("hasta") LocalDate hasta,
            @Param("ahora") Instant ahora);

    @Query("""
            select t from Transaccion t
            where t.cuenta.id = :cuentaId
              and t.estado = com.presupuesto.transaccion.entity.EstadoTransaccion.NO_CONCILIADA
            order by t.fecha desc, t.id desc
            """)
    List<Transaccion> noConciliadasDeCuenta(@Param("cuentaId") Long cuentaId, Pageable limite);

    @Query("""
            select count(t) from Transaccion t
            where t.cuenta.id = :cuentaId
              and t.estado = com.presupuesto.transaccion.entity.EstadoTransaccion.NO_CONCILIADA
            """)
    long contarNoConciliadasDeCuenta(@Param("cuentaId") Long cuentaId);

    interface SumaPorCuenta {

        Long getCuentaId();

        Long getTotal();

        Long getConciliado();
    }
}
