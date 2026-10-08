package com.presupuesto.usuario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nuevo nombre de la persona autenticada. El constructor compacto lo recorta antes de validar,
 * igual que el registro: las longitudes se aplican al valor ya recortado.
 */
public record ActualizarNombreRequest(@NotBlank @Size(min = 2, max = 100) String nombre) {

    public ActualizarNombreRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
