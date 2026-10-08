package com.presupuesto.reporte.dto.response;

import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import java.util.List;

/**
 * Una meta con su cumplimiento mes a mes y el total del rango (milésimas). {@code monto} es el de
 * la meta vigente; {@code porcentaje} es el del total del rango.
 */
public record MetaCumplimientoResponse(
        Long categoriaId,
        String nombre,
        boolean oculta,
        TipoMeta tipo,
        long monto,
        long necesidad,
        long asignado,
        long gastado,
        Long porcentaje,
        List<MesMetaResponse> meses) {

    public static MetaCumplimientoResponse desde(
            Meta meta,
            long necesidad,
            long asignado,
            long gastado,
            Long porcentaje,
            List<MesMetaResponse> meses) {
        return new MetaCumplimientoResponse(
                meta.getCategoria().getId(),
                meta.getCategoria().getNombre(),
                meta.getCategoria().isOculta(),
                meta.getTipo(),
                meta.getMonto(),
                necesidad,
                asignado,
                gastado,
                porcentaje,
                meses);
    }
}
