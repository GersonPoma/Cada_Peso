package com.presupuesto.conciliacion.dto.response;

import com.presupuesto.conciliacion.entity.Conciliacion;
import java.time.Instant;
import java.time.LocalDate;

public record ConciliacionResponse(
        Long id,
        Long cuentaId,
        LocalDate fecha,
        long saldoExtracto,
        long ajuste,
        Long transaccionAjusteId,
        int cantidadReconciliadas,
        Instant fechaCreacion) {

    public static ConciliacionResponse desde(Conciliacion conciliacion) {
        return new ConciliacionResponse(
                conciliacion.getId(),
                conciliacion.getCuenta().getId(),
                conciliacion.getFecha(),
                conciliacion.getSaldoExtracto(),
                conciliacion.getAjuste(),
                conciliacion.getTransaccionAjusteId(),
                conciliacion.getCantidadReconciliadas(),
                conciliacion.getFechaCreacion());
    }
}
