package com.presupuesto.meta.dto.request;

import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.validacion.MetaCoherente;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * Meta de una categoría. El constructor descarta (deja en {@code null}) los campos que no aplican
 * al tipo antes de validar, así un valor sobrante no genera errores ni se guarda. {@code monto}
 * va en milésimas.
 */
@MetaCoherente
public record GuardarMetaRequest(
        @NotNull TipoMeta tipo,
        @NotNull @Positive Long monto,
        FrecuenciaMeta frecuencia,
        @Min(1) @Max(7) Integer diaSemana,
        @Min(2) @Max(365) Integer intervaloDias,
        LocalDate fechaInicio,
        LocalDate fechaObjetivo) {

    public GuardarMetaRequest {
        if (tipo != TipoMeta.MONTO_MENSUAL) {
            frecuencia = null;
        }
        if (frecuencia != FrecuenciaMeta.SEMANAL) {
            diaSemana = null;
        }
        if (frecuencia != FrecuenciaMeta.PERSONALIZADA) {
            intervaloDias = null;
            fechaInicio = null;
        }
        if (tipo != TipoMeta.MONTO_PARA_FECHA) {
            fechaObjetivo = null;
        }
    }
}
