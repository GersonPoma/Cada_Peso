package com.presupuesto.cuenta.dto.request;

import com.presupuesto.cuenta.entity.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para editar una cuenta: solo nombre y tipo. Cualquier otra propiedad del JSON (como
 * {@code saldoInicial} o {@code enPresupuesto}) se ignora.
 */
public record ActualizarCuentaRequest(
        @NotBlank @Size(max = 100) String nombre, @NotNull TipoCuenta tipo) {

    public ActualizarCuentaRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
