package com.presupuesto.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Código de moneda ISO 4217 existente según {@code java.util.Currency}, escrito exactamente en
 * mayúsculas (ej. {@code BOB}). No basta con que tenga tres letras: {@code ZZZ} se rechaza.
 */
@Documented
@Constraint(validatedBy = MonedaValidaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MonedaValida {

    String message() default "No es un código de moneda ISO 4217 válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
