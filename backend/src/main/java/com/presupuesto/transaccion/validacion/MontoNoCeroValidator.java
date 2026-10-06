package com.presupuesto.transaccion.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MontoNoCeroValidator implements ConstraintValidator<MontoNoCero, Long> {

    @Override
    public boolean isValid(Long valor, ConstraintValidatorContext contexto) {
        return valor == null || valor != 0L;
    }
}
