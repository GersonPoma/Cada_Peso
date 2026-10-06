package com.presupuesto.transaccion.dto.request;

import com.presupuesto.transaccion.validacion.MontoNoCero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos para editar una transacción: la cuenta, el estado y {@code aprobada} no cambian por
 * aquí. Las subtransacciones reemplazan a las existentes.
 */
public record ActualizarTransaccionRequest(
        @NotNull LocalDate fecha,
        @NotNull @MontoNoCero Long monto,
        Long categoriaId,
        @Size(max = 100) String beneficiario,
        @Size(max = 500) String memo,
        List<@NotNull @Valid SubTransaccionRequest> subtransacciones) {

    public ActualizarTransaccionRequest {
        beneficiario = Normalizacion.recortarONulo(beneficiario);
        memo = Normalizacion.recortarONulo(memo);
        subtransacciones = subtransacciones == null ? List.of() : subtransacciones;
    }
}
