package com.presupuesto.meta.dto.response;

import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import java.time.LocalDate;

/** La meta de una categoría; los campos que no aplican a su tipo van en {@code null}. */
public record MetaResponse(
        Long categoriaId,
        TipoMeta tipo,
        long monto,
        FrecuenciaMeta frecuencia,
        Integer diaSemana,
        Integer intervaloDias,
        LocalDate fechaInicio,
        LocalDate fechaObjetivo) {

    public static MetaResponse desde(Meta meta) {
        return new MetaResponse(
                meta.getCategoria().getId(),
                meta.getTipo(),
                meta.getMonto(),
                meta.getFrecuencia(),
                meta.getDiaSemana(),
                meta.getIntervaloDias(),
                meta.getFechaInicio(),
                meta.getFechaObjetivo());
    }
}
