package com.presupuesto.presupuesto.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para renombrar un presupuesto. Solo se edita el nombre: cualquier otra propiedad del
 * cuerpo (como la moneda) se ignora.
 */
public record ActualizarPresupuestoRequest(@NotBlank @Size(max = 100) String nombre) {

    public ActualizarPresupuestoRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
