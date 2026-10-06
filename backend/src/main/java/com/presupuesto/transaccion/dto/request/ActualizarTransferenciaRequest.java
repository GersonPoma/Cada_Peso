package com.presupuesto.transaccion.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Datos para editar una transferencia; las cuentas no se cambian. Memo recortado como al crear. */
public record ActualizarTransferenciaRequest(
        @NotNull LocalDate fecha,
        @NotNull @Positive Long monto,
        Long categoriaId,
        @Size(max = 500) String memo) {

    public ActualizarTransferenciaRequest {
        memo = Normalizacion.recortarONulo(memo);
    }
}
