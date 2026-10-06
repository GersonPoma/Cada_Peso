package com.presupuesto.presupuesto.dto.response;

import com.presupuesto.presupuesto.entity.Presupuesto;
import java.time.Instant;

public record PresupuestoResponse(
        Long id, String nombre, String moneda, Instant fechaCreacion, Instant fechaActualizacion) {

    public static PresupuestoResponse desde(Presupuesto presupuesto) {
        return new PresupuestoResponse(
                presupuesto.getId(),
                presupuesto.getNombre(),
                presupuesto.getMoneda(),
                presupuesto.getFechaCreacion(),
                presupuesto.getFechaActualizacion());
    }
}
