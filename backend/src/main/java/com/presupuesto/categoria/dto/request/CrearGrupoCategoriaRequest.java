package com.presupuesto.categoria.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear un grupo. El constructor compacto recorta el nombre antes de validarlo. */
public record CrearGrupoCategoriaRequest(@NotBlank @Size(max = 100) String nombre) {

    public CrearGrupoCategoriaRequest {
        nombre = Normalizacion.nombre(nombre);
    }
}
