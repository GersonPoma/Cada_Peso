package com.presupuesto.beneficiario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un beneficiario. El constructor compacto recorta el nombre antes de
 * validarlo; {@code categoriaId} es su categoría predeterminada, opcional.
 */
public record CrearBeneficiarioRequest(
        @NotBlank @Size(max = 100) String nombre, Long categoriaId) {

    public CrearBeneficiarioRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
