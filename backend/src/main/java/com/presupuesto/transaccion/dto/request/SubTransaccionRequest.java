package com.presupuesto.transaccion.dto.request;

import com.presupuesto.transaccion.validacion.MontoNoCero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Una parte de una transacción dividida; {@code monto} en milésimas, con signo y no 0. */
public record SubTransaccionRequest(
        Long categoriaId,
        @NotNull @MontoNoCero Long monto,
        @Size(max = 500) String memo) {

    public SubTransaccionRequest {
        memo = Normalizacion.recortarONulo(memo);
    }
}
