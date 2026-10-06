package com.presupuesto.categoria.dto.response;

import com.presupuesto.categoria.entity.GrupoCategoria;
import java.time.Instant;

public record GrupoCategoriaResponse(
        Long id,
        String nombre,
        int orden,
        boolean oculto,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static GrupoCategoriaResponse desde(GrupoCategoria grupo) {
        return new GrupoCategoriaResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getOrden(),
                grupo.isOculto(),
                grupo.getFechaCreacion(),
                grupo.getFechaActualizacion());
    }
}
