package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;
import java.util.List;

/**
 * Gasto neto por categoría de un rango de meses (milésimas), agrupado por grupo en el orden del
 * árbol, con el gasto sin categoría aparte. {@code total} suma los grupos y el cubo sin
 * categoría; los porcentajes son centésimas de punto porcentual de ese total.
 */
public record GastoPorCategoriaResponse(
        String desde,
        String hasta,
        long total,
        List<GrupoGastoResponse> grupos,
        SinCategoriaGastoResponse sinCategoria) {

    public static GastoPorCategoriaResponse desde(
            YearMonth desde,
            YearMonth hasta,
            List<GrupoGastoResponse> grupos,
            SinCategoriaGastoResponse sinCategoria) {
        long total = sinCategoria.total()
                + grupos.stream().mapToLong(GrupoGastoResponse::total).sum();
        return new GastoPorCategoriaResponse(
                desde.toString(), hasta.toString(), total, grupos, sinCategoria);
    }
}
