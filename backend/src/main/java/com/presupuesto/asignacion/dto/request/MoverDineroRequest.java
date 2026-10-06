package com.presupuesto.asignacion.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Mueve {@code monto} (milésimas, mayor que 0) del asignado de una categoría a otra. */
public record MoverDineroRequest(
        @NotNull Long origenId,
        @NotNull Long destinoId,
        @NotNull @Positive Long monto) {}
