package com.presupuesto.meta.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Auto-asignar el mes. Sin {@code categoriaIds} se consideran todas las categorías visibles
 * (una lista vacía es inválida); {@code simular} ausente vale {@code false}.
 */
public record AutoAsignarRequest(
        @NotNull EstrategiaAutoAsignar estrategia,
        @Size(min = 1) List<@NotNull Long> categoriaIds,
        Boolean simular) {

    public AutoAsignarRequest {
        if (simular == null) {
            simular = false;
        }
    }
}
