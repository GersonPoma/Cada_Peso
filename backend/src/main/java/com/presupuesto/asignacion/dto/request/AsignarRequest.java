package com.presupuesto.asignacion.dto.request;

import jakarta.validation.constraints.NotNull;

/** Asignado de la categoría en el mes, en milésimas; puede ser 0 o negativo. */
public record AsignarRequest(@NotNull Long asignado) {}
