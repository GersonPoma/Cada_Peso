package com.presupuesto.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El texto no puede ocupar más de {@link #value()} bytes en UTF-8. A diferencia de {@code @Size},
 * que cuenta caracteres, cuenta bytes: una {@code ñ} o una vocal con tilde ocupan 2.
 */
@Documented
@Constraint(validatedBy = MaximoBytesUtf8Validator.class)
@Target({
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT,
    ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaximoBytesUtf8 {

    int value();

    String message() default "No puede ocupar más de {value} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
