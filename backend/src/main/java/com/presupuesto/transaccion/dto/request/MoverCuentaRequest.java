package com.presupuesto.transaccion.dto.request;

import jakarta.validation.constraints.NotNull;

public record MoverCuentaRequest(@NotNull Long cuentaId) {}
