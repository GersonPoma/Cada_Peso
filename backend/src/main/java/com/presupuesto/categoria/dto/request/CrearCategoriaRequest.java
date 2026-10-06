package com.presupuesto.categoria.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una categoría. El constructor compacto recorta el nombre y la nota; una nota
 * vacía queda en {@code null}.
 */
public record CrearCategoriaRequest(
        @NotNull Long grupoId,
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 500) String nota) {

    public CrearCategoriaRequest {
        nombre = Normalizacion.nombre(nombre);
        nota = Normalizacion.nota(nota);
    }
}
