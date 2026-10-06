package com.presupuesto.asignacion.repository;

import com.presupuesto.transaccion.entity.Transaccion;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas agregadas sobre las transacciones para el cálculo del presupuesto mensual. Todas se
 * acotan por presupuesto y solo cuentan cuentas con {@code enPresupuesto} (abiertas y cerradas);
 * nunca cargan las transacciones en memoria.
 */
public interface ActividadMensualRepository extends Repository<Transaccion, Long> {

    /** Suma por categoría y mes calendario de las transacciones con categoría. */
    @Query("""
            select t.categoria.id as categoriaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(t.monto) as total
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.categoria is not null
              and t.fecha <= :hasta
            group by t.categoria.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<ActividadPorMes> actividadSimple(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /** Igual, sobre las subtransacciones con categoría (por la fecha de su transacción). */
    @Query("""
            select s.categoria.id as categoriaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(s.monto) as total
            from SubTransaccion s join s.transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and s.categoria is not null
              and t.fecha <= :hasta
            group by s.categoria.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<ActividadPorMes> actividadDividida(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /**
     * Entradas sin categoría y sin subtransacciones hasta una fecha (inclusive). Excluye la
     * entrada de una transferencia cuya pata par está en una cuenta del presupuesto (mover dinero
     * dentro del presupuesto no es un ingreso); {@code not exists} y no navegar a la pata par,
     * porque la navegación implícita haría un inner join y descartaría las que no tienen par.
     */
    @Query("""
            select coalesce(sum(t.monto), 0L)
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.monto > 0
              and t.categoria is null
              and not exists (select 1 from SubTransaccion s where s.transaccion = t)
              and not exists (select 1 from Transaccion p
                              where p = t.transaccionPar and p.cuenta.enPresupuesto = true)
              and t.fecha <= :hasta
            """)
    long ingresosSinCategoria(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /** Saldos iniciales positivos de las cuentas del presupuesto que no son tarjeta de crédito. */
    @Query("""
            select coalesce(sum(c.saldoInicial), 0L)
            from Cuenta c
            where c.presupuesto.id = :presupuestoId
              and c.enPresupuesto = true
              and c.saldoInicial > 0
              and c.tipo <> com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
            """)
    long saldosInicialesPositivos(@Param("presupuestoId") Long presupuestoId);

    interface ActividadPorMes {

        Long getCategoriaId();

        Integer getAnio();

        Integer getMes();

        Long getTotal();
    }
}
