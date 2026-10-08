package com.presupuesto.transaccionprogramada.dto.request;

import com.presupuesto.transaccion.validacion.MontoNoCero;
import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos para editar una transacción programada. La cuenta y la fecha de inicio no se editan: si
 * llegan en el cuerpo se ignoran. Beneficiario, memo, categoría y fecha de fin se reemplazan
 * (nulo los quita).
 */
public record ActualizarProgramadaRequest(
        @NotNull @MontoNoCero Long monto,
        Long categoriaId,
        @Size(max = 100) String beneficiario,
        @Size(max = 500) String memo,
        @NotNull FrecuenciaProgramada frecuencia,
        LocalDate fechaFin) {

    public ActualizarProgramadaRequest {
        beneficiario = Normalizacion.recortarONulo(beneficiario);
        memo = Normalizacion.recortarONulo(memo);
    }
}
