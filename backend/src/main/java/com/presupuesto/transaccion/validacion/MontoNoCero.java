package com.presupuesto.transaccion.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Monto en milésimas distinto de 0. Un valor nulo lo valida {@code @NotNull}, no esta regla. */
@Documented
@Constraint(validatedBy = MontoNoCeroValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
    ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MontoNoCero {

    String message() default "El monto no puede ser 0";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
