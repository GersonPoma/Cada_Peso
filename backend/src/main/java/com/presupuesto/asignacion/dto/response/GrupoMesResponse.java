package com.presupuesto.asignacion.dto.response;

import com.presupuesto.categoria.entity.GrupoCategoria;
import java.util.List;

/** Un grupo con sus categorías del mes, en el orden en que se reciben. */
public record GrupoMesResponse(
        Long id,
        String nombre,
        int orden,
        boolean oculto,
        List<CategoriaMesResponse> categorias) {

    public static GrupoMesResponse desde(
            GrupoCategoria grupo, List<CategoriaMesResponse> categorias) {
        return new GrupoMesResponse(
                grupo.getId(), grupo.getNombre(), grupo.getOrden(), grupo.isOculto(), categorias);
    }
}
