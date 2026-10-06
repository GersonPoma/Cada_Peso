package com.presupuesto.categoria.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Nueva posición (base 0) del grupo; el límite superior lo comprueba el service. */
public record MoverGrupoCategoriaRequest(@NotNull @PositiveOrZero Integer posicion) {
}
