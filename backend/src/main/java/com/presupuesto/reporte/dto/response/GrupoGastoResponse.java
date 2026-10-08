package com.presupuesto.reporte.dto.response;

import com.presupuesto.categoria.entity.GrupoCategoria;
import java.util.List;
import java.util.function.LongUnaryOperator;

/** Gasto de un grupo: el total es la suma de las categorías que trae (milésimas). */
public record GrupoGastoResponse(
        Long grupoId,
        String nombre,
        long total,
        long porcentaje,
        List<CategoriaGastoResponse> categorias) {

    public static GrupoGastoResponse desde(
            GrupoCategoria grupo,
            List<CategoriaGastoResponse> categorias,
            LongUnaryOperator porcentajeDe) {
        long total = categorias.stream().mapToLong(CategoriaGastoResponse::total).sum();
        return new GrupoGastoResponse(
                grupo.getId(), grupo.getNombre(), total, porcentajeDe.applyAsLong(total),
                categorias);
    }
}
