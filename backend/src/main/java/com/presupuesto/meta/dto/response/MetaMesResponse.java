package com.presupuesto.meta.dto.response;

import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;

/** Una meta en un mes, con sus cifras ya calculadas (milésimas). */
public record MetaMesResponse(
        Long categoriaId,
        String nombre,
        TipoMeta tipo,
        long monto,
        long necesidad,
        long asignado,
        long disponible,
        long faltante,
        EstadoMeta estado) {

    public static MetaMesResponse desde(
            Meta meta,
            long necesidad,
            long asignado,
            long disponible,
            long faltante,
            EstadoMeta estado) {
        return new MetaMesResponse(
                meta.getCategoria().getId(),
                meta.getCategoria().getNombre(),
                meta.getTipo(),
                meta.getMonto(),
                necesidad,
                asignado,
                disponible,
                faltante,
                estado);
    }
}
