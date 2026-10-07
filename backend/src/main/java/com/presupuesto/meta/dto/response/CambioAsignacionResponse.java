package com.presupuesto.meta.dto.response;

import com.presupuesto.categoria.entity.Categoria;

/** El asignado de una categoría antes y después de auto-asignar (milésimas). */
public record CambioAsignacionResponse(
        Long categoriaId, String nombre, long asignadoAntes, long asignadoDespues) {

    public static CambioAsignacionResponse desde(
            Categoria categoria, long asignadoAntes, long asignadoDespues) {
        return new CambioAsignacionResponse(
                categoria.getId(), categoria.getNombre(), asignadoAntes, asignadoDespues);
    }
}
