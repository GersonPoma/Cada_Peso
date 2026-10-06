package com.presupuesto.categoria.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para renombrar un grupo. Cualquier otra propiedad del JSON (como {@code orden} u
 * {@code oculto}) se ignora.
 */
public record ActualizarGrupoCategoriaRequest(@NotBlank @Size(max = 100) String nombre) {

    public ActualizarGrupoCategoriaRequest {
        nombre = Normalizacion.nombre(nombre);
    }
}
