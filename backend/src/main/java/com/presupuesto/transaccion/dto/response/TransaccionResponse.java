package com.presupuesto.transaccion.dto.response;

import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TransaccionResponse(
        Long id,
        Long cuentaId,
        LocalDate fecha,
        long monto,
        Long categoriaId,
        String beneficiario,
        String memo,
        EstadoTransaccion estado,
        boolean aprobada,
        List<SubTransaccionResponse> subtransacciones,
        Long transaccionParId,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static TransaccionResponse desde(Transaccion transaccion) {
        return new TransaccionResponse(
                transaccion.getId(),
                transaccion.getCuenta().getId(),
                transaccion.getFecha(),
                transaccion.getMonto(),
                transaccion.getCategoria() == null ? null : transaccion.getCategoria().getId(),
                transaccion.getBeneficiario(),
                transaccion.getMemo(),
                transaccion.getEstado(),
                transaccion.isAprobada(),
                transaccion.getSubtransacciones().stream()
                        .map(SubTransaccionResponse::desde)
                        .toList(),
                transaccion.getTransaccionPar() == null
                        ? null
                        : transaccion.getTransaccionPar().getId(),
                transaccion.getFechaCreacion(),
                transaccion.getFechaActualizacion());
    }
}
