package com.presupuesto.usuario.validacion;

import com.presupuesto.comun.validacion.MaximoBytesUtf8;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reglas de una contraseña nueva: no vacía, de 8 a 72 caracteres y de máximo 72 bytes en UTF-8
 * (el límite real de BCrypt). Es la única definición, usada por el registro y por el cambio de
 * contraseña. No es {@code @ReportAsSingleViolation}: cada regla conserva su propio mensaje.
 */
@Documented
@Constraint(validatedBy = {})
@NotBlank
@Size(min = 8, max = 72)
@MaximoBytesUtf8(72)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaValida {

    String message() default "Contraseña no válida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
