package com.presupuesto.transaccionprogramada.dto.response;

import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import java.time.Instant;
import java.time.LocalDate;

public record TransaccionProgramadaResponse(
        Long id,
        Long cuentaId,
        LocalDate fechaInicio,
        FrecuenciaProgramada frecuencia,
        LocalDate fechaFin,
        long monto,
        Long categoriaId,
        String beneficiario,
        String memo,
        boolean activa,
        LocalDate proximaFecha,
        String ultimoError,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static TransaccionProgramadaResponse desde(TransaccionProgramada programada) {
        return new TransaccionProgramadaResponse(
                programada.getId(),
                programada.getCuenta().getId(),
                programada.getFechaInicio(),
                programada.getFrecuencia(),
                programada.getFechaFin(),
                programada.getMonto(),
                programada.getCategoria() == null ? null : programada.getCategoria().getId(),
                programada.getBeneficiario(),
                programada.getMemo(),
                programada.isActiva(),
                programada.getProximaFecha(),
                programada.getUltimoError(),
                programada.getFechaCreacion(),
                programada.getFechaActualizacion());
    }
}
