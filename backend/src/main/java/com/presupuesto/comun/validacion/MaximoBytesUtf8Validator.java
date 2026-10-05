package com.presupuesto.comun.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaximoBytesUtf8Validator implements ConstraintValidator<MaximoBytesUtf8, String> {

    private int maximoBytes;

    @Override
    public void initialize(MaximoBytesUtf8 anotacion) {
        this.maximoBytes = anotacion.value();
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= maximoBytes;
    }
}
