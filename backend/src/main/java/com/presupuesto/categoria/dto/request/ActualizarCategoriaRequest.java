package com.presupuesto.categoria.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para editar una categoría: solo nombre y nota. Cualquier otra propiedad del JSON (como
 * {@code grupoId} u {@code orden}) se ignora.
 */
public record ActualizarCategoriaRequest(
        @NotBlank @Size(max = 100) String nombre, @Size(max = 500) String nota) {

    public ActualizarCategoriaRequest {
        nombre = Normalizacion.nombre(nombre);
        nota = Normalizacion.nota(nota);
    }
}
