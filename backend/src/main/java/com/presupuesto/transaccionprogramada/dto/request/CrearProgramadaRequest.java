package com.presupuesto.transaccionprogramada.dto.request;

import com.presupuesto.transaccion.validacion.MontoNoCero;
import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos para crear una transacción programada. El constructor compacto recorta beneficiario y
 * memo (vacío pasa a {@code null}). Que {@code fechaFin} no sea anterior a {@code fechaInicio}
 * lo comprueba el service.
 */
public record CrearProgramadaRequest(
        @NotNull Long cuentaId,
        @NotNull LocalDate fechaInicio,
        @NotNull FrecuenciaProgramada frecuencia,
        LocalDate fechaFin,
        @NotNull @MontoNoCero Long monto,
        Long categoriaId,
        @Size(max = 100) String beneficiario,
        @Size(max = 500) String memo) {

    public CrearProgramadaRequest {
        beneficiario = Normalizacion.recortarONulo(beneficiario);
        memo = Normalizacion.recortarONulo(memo);
    }
}
