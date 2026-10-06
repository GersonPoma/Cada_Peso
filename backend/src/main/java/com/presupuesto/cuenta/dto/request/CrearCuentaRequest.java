package com.presupuesto.cuenta.dto.request;

import com.presupuesto.cuenta.entity.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cuenta. El constructor compacto recorta el nombre antes de validarlo y
 * aplica los valores por defecto: {@code enPresupuesto = true} y {@code saldoInicial = 0}
 * (milésimas).
 */
public record CrearCuentaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull TipoCuenta tipo,
        Boolean enPresupuesto,
        Long saldoInicial) {

    public CrearCuentaRequest {
        nombre = nombre == null ? null : nombre.strip();
        enPresupuesto = enPresupuesto == null ? Boolean.TRUE : enPresupuesto;
        saldoInicial = saldoInicial == null ? Long.valueOf(0L) : saldoInicial;
    }
}
