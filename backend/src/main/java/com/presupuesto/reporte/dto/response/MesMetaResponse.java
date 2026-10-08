package com.presupuesto.reporte.dto.response;

import com.presupuesto.meta.dto.response.EstadoMeta;
import java.time.YearMonth;

/**
 * Cumplimiento de una meta en un mes (milésimas). {@code porcentaje} es asignado sobre
 * necesidad en centésimas de punto, sin tope, y {@code null} si la necesidad es 0.
 */
public record MesMetaResponse(
        String mes,
        long necesidad,
        long asignado,
        long gastado,
        long disponible,
        long faltante,
        EstadoMeta estado,
        Long porcentaje) {

    public static MesMetaResponse desde(
            YearMonth mes,
            long necesidad,
            long asignado,
            long gastado,
            long disponible,
            long faltante,
            EstadoMeta estado,
            Long porcentaje) {
        return new MesMetaResponse(
                mes.toString(), necesidad, asignado, gastado, disponible, faltante, estado,
                porcentaje);
    }
}
