package com.presupuesto.categoria.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Grupo destino y posición (base 0) en él; el límite superior lo comprueba el service. */
public record MoverCategoriaRequest(
        @NotNull Long grupoId, @NotNull @PositiveOrZero Integer posicion) {
}
