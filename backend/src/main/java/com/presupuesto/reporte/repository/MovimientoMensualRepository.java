package com.presupuesto.reporte.repository;

import com.presupuesto.transaccion.entity.Transaccion;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Movimientos de las cuentas agrupados por mes calendario, de cualquier estado de transacción.
 * Sin cota inferior: el saldo de un mes necesita todo lo anterior. Cada fila resume un mes de una
 * cuenta, así que el volumen es cuentas por meses con movimientos, no transacciones.
 */
public interface MovimientoMensualRepository extends Repository<Transaccion, Long> {

    /**
     * Entradas (montos positivos) y salidas (negativos, con signo) por cuenta y mes de todas las
     * cuentas del presupuesto (cualquier tipo, abiertas o cerradas, dentro o fuera de él) hasta
     * una fecha inclusive.
     */
    @Query("""
            select t.cuenta.id as cuentaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(case when t.monto > 0 then t.monto else 0L end) as entradas,
                   sum(case when t.monto < 0 then t.monto else 0L end) as salidas
            from Transaccion t
            where t.cuenta.presupuesto.id = :presupuestoId
              and t.fecha <= :hasta
            group by t.cuenta.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<MovimientoPorMes> movimientoPorCuentaYMes(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /** Lo mismo para una sola cuenta (usa el índice por cuenta y fecha). */
    @Query("""
            select t.cuenta.id as cuentaId,
                   extract(year from t.fecha) as anio,
                   extract(month from t.fecha) as mes,
                   sum(case when t.monto > 0 then t.monto else 0L end) as entradas,
                   sum(case when t.monto < 0 then t.monto else 0L end) as salidas
            from Transaccion t
            where t.cuenta.id = :cuentaId
              and t.fecha <= :hasta
            group by t.cuenta.id, extract(year from t.fecha), extract(month from t.fecha)
            """)
    List<MovimientoPorMes> movimientoDeCuentaPorMes(
            @Param("cuentaId") Long cuentaId, @Param("hasta") LocalDate hasta);

    interface MovimientoPorMes {

        Long getCuentaId();

        Integer getAnio();

        Integer getMes();

        Long getEntradas();

        Long getSalidas();
    }
}
