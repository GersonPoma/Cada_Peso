package com.presupuesto.beneficiario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para editar un beneficiario. El constructor compacto recorta el nombre antes de
 * validarlo; un {@code categoriaId} nulo quita la categoría predeterminada.
 */
public record ActualizarBeneficiarioRequest(
        @NotBlank @Size(max = 100) String nombre, Long categoriaId) {

    public ActualizarBeneficiarioRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
