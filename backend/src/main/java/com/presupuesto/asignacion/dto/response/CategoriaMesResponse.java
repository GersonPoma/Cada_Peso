package com.presupuesto.asignacion.dto.response;

import com.presupuesto.categoria.entity.Categoria;

/** Una categoría en un mes, con sus cifras ya calculadas (milésimas). */
public record CategoriaMesResponse(
        Long categoriaId,
        String nombre,
        boolean oculta,
        long asignado,
        long actividad,
        long disponible,
        boolean sobregastada) {

    public static CategoriaMesResponse desde(
            Categoria categoria, long asignado, long actividad, long disponible) {
        return new CategoriaMesResponse(
                categoria.getId(),
                categoria.getNombre(),
                categoria.isOculta(),
                asignado,
                actividad,
                disponible,
                disponible < 0);
    }
}
