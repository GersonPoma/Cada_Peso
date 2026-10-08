package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;
import java.util.List;

/** Cumplimiento de las metas del presupuesto en un rango de meses, en el orden del árbol. */
public record CumplimientoMetasResponse(
        String desde, String hasta, List<MetaCumplimientoResponse> metas) {

    public static CumplimientoMetasResponse desde(
            YearMonth desde, YearMonth hasta, List<MetaCumplimientoResponse> metas) {
        return new CumplimientoMetasResponse(desde.toString(), hasta.toString(), metas);
    }
}
