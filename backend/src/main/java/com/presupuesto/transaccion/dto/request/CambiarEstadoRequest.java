package com.presupuesto.transaccion.dto.request;

import com.presupuesto.transaccion.entity.EstadoTransaccion;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull EstadoTransaccion estado) {}
