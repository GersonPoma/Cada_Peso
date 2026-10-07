package com.presupuesto.meta.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Los datos de la meta están completos según su tipo: {@code MONTO_MENSUAL} exige frecuencia
 * (semanal: día de la semana; personalizada: intervalo y fecha de inicio) y
 * {@code MONTO_PARA_FECHA} exige fecha objetivo. Cada violación se reporta en el campo que falta.
 */
@Documented
@Constraint(validatedBy = MetaCoherenteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetaCoherente {

    String message() default "Los datos de la meta no corresponden a su tipo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
