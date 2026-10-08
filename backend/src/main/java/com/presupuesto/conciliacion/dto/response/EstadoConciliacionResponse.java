package com.presupuesto.conciliacion.dto.response;

import com.presupuesto.transaccion.dto.response.TransaccionResponse;
import com.presupuesto.transaccion.entity.Transaccion;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de la conciliación de una cuenta, en milésimas. {@code diferencia} es
 * {@code saldoExtracto - saldoConciliadoAlCorte}; {@code noConciliadas} trae como máximo las más
 * recientes y {@code totalNoConciliadas} cuenta todas.
 */
public record EstadoConciliacionResponse(
        Long cuentaId,
        LocalDate fecha,
        long saldoExtracto,
        long saldoConciliado,
        long saldoConciliadoAlCorte,
        long diferencia,
        long totalNoConciliadas,
        List<TransaccionResponse> noConciliadas) {

    public static EstadoConciliacionResponse desde(
            Long cuentaId,
            LocalDate fecha,
            long saldoExtracto,
            long saldoConciliado,
            long saldoConciliadoAlCorte,
            long diferencia,
            long totalNoConciliadas,
            List<Transaccion> noConciliadas) {
        return new EstadoConciliacionResponse(
                cuentaId,
                fecha,
                saldoExtracto,
                saldoConciliado,
                saldoConciliadoAlCorte,
                diferencia,
                totalNoConciliadas,
                noConciliadas.stream().map(TransaccionResponse::desde).toList());
    }
}
