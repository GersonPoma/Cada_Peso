package com.presupuesto.usuario.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/**
 * Spring crea este validador con {@code SpringConstraintValidatorFactory}, que inyecta el
 * {@link Clock} por constructor.
 */
public class MayorDeEdadValidator implements ConstraintValidator<MayorDeEdad, LocalDate> {

    private static final int EDAD_MINIMA = 18;

    private final Clock clock;

    public MayorDeEdadValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(LocalDate fechaNacimiento, ConstraintValidatorContext contexto) {
        if (fechaNacimiento == null) {
            return true;
        }
        return Period.between(fechaNacimiento, LocalDate.now(clock)).getYears() >= EDAD_MINIMA;
    }
}
