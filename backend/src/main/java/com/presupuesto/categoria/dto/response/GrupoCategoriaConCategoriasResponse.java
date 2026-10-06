package com.presupuesto.categoria.dto.response;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import java.util.List;

/** Un grupo con sus categorías, en el orden en que se reciben. */
public record GrupoCategoriaConCategoriasResponse(
        Long id, String nombre, int orden, boolean oculto, List<CategoriaResponse> categorias) {

    public static GrupoCategoriaConCategoriasResponse desde(
            GrupoCategoria grupo, List<Categoria> categorias) {
        return new GrupoCategoriaConCategoriasResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getOrden(),
                grupo.isOculto(),
                categorias.stream().map(CategoriaResponse::desde).toList());
    }
}
