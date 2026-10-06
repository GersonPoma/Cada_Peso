package com.presupuesto.transaccion.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Operación sobre entre 1 y 100 transacciones; {@code categoriaId} solo para CATEGORIZAR. */
public record LoteRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> ids,
        @NotNull OperacionLote operacion,
        Long categoriaId) {}
