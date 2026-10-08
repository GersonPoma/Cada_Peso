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
     * dentro del presupuesto no es un ingreso) y toda entrada en una tarjeta de crédito (no entra
     * dinero a ninguna cuenta que no sea tarjeta); {@code not exists} y no navegar a la pata par,
     * porque la navegación implícita haría un inner join y descartaría las que no tienen par.
     */
    @Query("""
            select coalesce(sum(t.monto), 0L)
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.cuenta.tipo <> com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
              and t.monto > 0
              and t.categoria is null
              and not exists (select 1 from SubTransaccion s where s.transaccion = t)
              and not exists (select 1 from Transaccion p
                              where p = t.transaccionPar and p.cuenta.enPresupuesto = true)
              and t.fecha <= :hasta
            """)
    long ingresosSinCategoria(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /**
     * Lo mismo que {@link #ingresosSinCategoria}, pero por mes calendario y con cota inferior, más
     * las salidas: para cada mes, {@code ingresos} (entradas sin categoría de cuentas que no son
     * tarjeta) y {@code salidas} (montos negativos sin categoría, el gasto "Sin categoría"). Las
     * dos sumas comparten la cláusula de {@link #ingresosSinCategoria}: sin categoría, sin
     * subtransacciones y fuera de una transferencia con par en el presupuesto (ambas patas).
     * Mantener en sincronía con {@link #ingresosSinCategoria}; un test compara las dos.
     */
    @Query("""
            select extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(case when t.monto > 0
                             and t.cuenta.tipo <> com.presupuesto.cuenta.entity
                                 .TipoCuenta.TARJETA_CREDITO
                            then t.monto else 0L end) as ingresos,
                   sum(case when t.monto < 0 then t.monto else 0L end) as salidas
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.categoria is null
              and not exists (select 1 from SubTransaccion s where s.transaccion = t)
              and not exists (select 1 from Transaccion p
                              where p = t.transaccionPar and p.cuenta.enPresupuesto = true)
              and t.fecha between :desde and :hasta
            group by extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<SinCategoriaPorMes> sinCategoriaPorMes(
            @Param("presupuestoId") Long presupuestoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Suma por mes de las partes de transacciones divididas sin categoría y con monto negativo
     * (gasto "Sin categoría") de cuentas del presupuesto, por la fecha de su transacción.
     */
    @Query("""
            select extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(s.monto) as total
            from SubTransaccion s join s.transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and s.categoria is null
              and s.monto < 0
              and t.fecha between :desde and :hasta
            group by extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<TotalPorMes> salidasDivididasSinCategoria(
            @Param("presupuestoId") Long presupuestoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

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

    /**
     * Suma por tarjeta de crédito y mes de las transacciones con categoría hechas con ella: la
     * base de la reserva de su categoría de pago. Solo tarjetas del presupuesto (abiertas y
     * cerradas).
     */
    @Query("""
            select t.cuenta.id as cuentaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(t.monto) as total
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.cuenta.tipo = com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
              and t.categoria is not null
              and t.fecha <= :hasta
            group by t.cuenta.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<SumaTarjetaPorMes> gastosTarjeta(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /** Igual, sobre las subtransacciones con categoría (por la fecha de su transacción). */
    @Query("""
            select t.cuenta.id as cuentaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(s.monto) as total
            from SubTransaccion s join s.transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.cuenta.tipo = com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
              and s.categoria is not null
              and t.fecha <= :hasta
            group by t.cuenta.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<SumaTarjetaPorMes> gastosTarjetaDivididos(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /**
     * Suma por tarjeta y mes de sus patas de transferencia cuya otra pata está en una cuenta del
     * presupuesto (los pagos, que entran positivos). {@code exists} y no navegar a la pata par,
     * por el inner join implícito.
     */
    @Query("""
            select t.cuenta.id as cuentaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(t.monto) as total
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.cuenta.enPresupuesto = true
              and t.cuenta.tipo = com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
              and t.transaccionPar is not null
              and exists (select 1 from Transaccion p
                          where p = t.transaccionPar and p.cuenta.enPresupuesto = true)
              and t.fecha <= :hasta
            group by t.cuenta.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<SumaTarjetaPorMes> pagosATarjeta(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    interface SinCategoriaPorMes {

        Integer getAnio();

        Integer getMes();

        Long getIngresos();

        Long getSalidas();
    }

    interface TotalPorMes {

        Integer getAnio();

        Integer getMes();

        Long getTotal();
    }

    interface SumaTarjetaPorMes {

        Long getCuentaId();

        Integer getAnio();

        Integer getMes();

        Long getTotal();
    }

    interface ActividadPorMes {

        Long getCategoriaId();

        Integer getAnio();

        Integer getMes();

        Long getTotal();
    }
}
