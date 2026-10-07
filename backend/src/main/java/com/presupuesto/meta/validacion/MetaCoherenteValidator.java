package com.presupuesto.meta.validacion;

import com.presupuesto.meta.dto.request.GuardarMetaRequest;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida el request ya normalizado (sin los campos que no aplican al tipo). Sin tipo no dice
 * nada: lo cubre {@code @NotNull}. Los rangos de {@code diaSemana} e {@code intervaloDias} los
 * validan sus propias anotaciones.
 */
public class MetaCoherenteValidator
        implements ConstraintValidator<MetaCoherente, GuardarMetaRequest> {

    static final String FRECUENCIA_OBLIGATORIA = "La frecuencia es obligatoria";
    static final String DIA_SEMANA_OBLIGATORIO = "El día de la semana es obligatorio";
    static final String INTERVALO_OBLIGATORIO = "El intervalo en días es obligatorio";
    static final String FECHA_INICIO_OBLIGATORIA = "La fecha de inicio es obligatoria";
    static final String FECHA_OBJETIVO_OBLIGATORIA = "La fecha objetivo es obligatoria";

    @Override
    public boolean isValid(GuardarMetaRequest request, ConstraintValidatorContext contexto) {
        if (request == null || request.tipo() == null) {
            return true;
        }
        contexto.disableDefaultConstraintViolation();
        boolean valido = true;
        switch (request.tipo()) {
            case MONTO_MENSUAL -> valido = validarFrecuencia(request, contexto);
            case MONTO_PARA_FECHA -> {
                if (request.fechaObjetivo() == null) {
                    valido = rechazar(contexto, "fechaObjetivo", FECHA_OBJETIVO_OBLIGATORIA);
                }
            }
            case SALDO_OBJETIVO -> { }
        }
        return valido;
    }

    private static boolean validarFrecuencia(
            GuardarMetaRequest request, ConstraintValidatorContext contexto) {
        FrecuenciaMeta frecuencia = request.frecuencia();
        if (frecuencia == null) {
            return rechazar(contexto, "frecuencia", FRECUENCIA_OBLIGATORIA);
        }
        boolean valido = true;
        if (frecuencia == FrecuenciaMeta.SEMANAL && request.diaSemana() == null) {
            valido = rechazar(contexto, "diaSemana", DIA_SEMANA_OBLIGATORIO);
        }
        if (frecuencia == FrecuenciaMeta.PERSONALIZADA) {
            if (request.intervaloDias() == null) {
                valido = rechazar(contexto, "intervaloDias", INTERVALO_OBLIGATORIO);
            }
            if (request.fechaInicio() == null) {
                valido = rechazar(contexto, "fechaInicio", FECHA_INICIO_OBLIGATORIA);
            }
        }
        return valido;
    }

    /** Agrega la violación al campo y devuelve {@code false} para encadenar el resultado. */
    private static boolean rechazar(
            ConstraintValidatorContext contexto, String campo, String mensaje) {
        contexto.buildConstraintViolationWithTemplate(mensaje)
                .addPropertyNode(campo)
                .addConstraintViolation();
        return false;
    }
}
