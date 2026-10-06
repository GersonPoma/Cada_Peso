package com.presupuesto.transaccion.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos para crear una transferencia. El {@code monto} es el valor absoluto en milésimas. El
 * constructor compacto recorta el memo (vacío pasa a {@code null}). Que origen y destino sean
 * distintos y la regla de categoría los comprueba el service.
 */
public record CrearTransferenciaRequest(
        @NotNull Long cuentaOrigenId,
        @NotNull Long cuentaDestinoId,
        @NotNull LocalDate fecha,
        @NotNull @Positive Long monto,
        Long categoriaId,
        @Size(max = 500) String memo) {

    public CrearTransferenciaRequest {
        memo = Normalizacion.recortarONulo(memo);
    }
}
