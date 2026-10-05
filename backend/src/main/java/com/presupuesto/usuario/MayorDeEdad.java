package com.presupuesto.usuario;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La fecha de nacimiento corresponde a una persona con 18 años cumplidos a la fecha actual del
 * sistema (el día del aniversario cuenta como cumplido).
 */
@Documented
@Constraint(validatedBy = MayorDeEdadValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MayorDeEdad {

    String message() default "Debe tener 18 años o más";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
