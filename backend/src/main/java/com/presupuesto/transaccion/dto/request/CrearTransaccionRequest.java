package com.presupuesto.transaccion.dto.request;

import com.presupuesto.transaccion.validacion.MontoNoCero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos para crear una transacción. El constructor compacto recorta beneficiario y memo (vacío
 * pasa a {@code null}) y aplica los valores por defecto: {@code aprobada = true} y sin
 * subtransacciones. Las reglas que solo el service puede comprobar (cantidad de subtransacciones,
 * categoría junto a una división, suma) no se validan aquí.
 */
public record CrearTransaccionRequest(
        @NotNull Long cuentaId,
        @NotNull LocalDate fecha,
        @NotNull @MontoNoCero Long monto,
        Long categoriaId,
        @Size(max = 100) String beneficiario,
        @Size(max = 500) String memo,
        Boolean aprobada,
        List<@NotNull @Valid SubTransaccionRequest> subtransacciones) {

    public CrearTransaccionRequest {
        beneficiario = Normalizacion.recortarONulo(beneficiario);
        memo = Normalizacion.recortarONulo(memo);
        aprobada = aprobada == null ? Boolean.TRUE : aprobada;
        subtransacciones = subtransacciones == null ? List.of() : subtransacciones;
    }
}
