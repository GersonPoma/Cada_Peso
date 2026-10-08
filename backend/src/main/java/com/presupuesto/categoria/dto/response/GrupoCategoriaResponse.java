package com.presupuesto.categoria.dto.response;

import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import java.time.Instant;

public record GrupoCategoriaResponse(
        Long id,
        String nombre,
        int orden,
        boolean oculto,
        TipoGrupoCategoria tipo,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static GrupoCategoriaResponse desde(GrupoCategoria grupo) {
        return new GrupoCategoriaResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getOrden(),
                grupo.isOculto(),
                grupo.getTipo(),
                grupo.getFechaCreacion(),
                grupo.getFechaActualizacion());
    }
}
