package com.presupuesto.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.usuario.validacion.MayorDeEdadValidator;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegistroRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void crearValidator() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC);
        Configuration<?> configuracion = Validation.byDefaultProvider().configure();
        ConstraintValidatorFactory porDefecto =
                configuracion.getDefaultConstraintValidatorFactory();
        validatorFactory = configuracion
                .constraintValidatorFactory(new ConstraintValidatorFactory() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> clase) {
                        if (clase == MayorDeEdadValidator.class) {
                            return (T) new MayorDeEdadValidator(reloj);
                        }
                        return porDefecto.getInstance(clase);
                    }

                    @Override
                    public void releaseInstance(ConstraintValidator<?, ?> instancia) {
                        porDefecto.releaseInstance(instancia);
                    }
                })
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void cerrarValidator() {
        validatorFactory.close();
    }

    @Test
    void elEmailSeRecortaYPasaAMinusculasAntesDeValidar() {
        RegistroRequest request = registro("  Ana@Ejemplo.COM  ", "secreta123", "Ana");

        assertThat(request.email()).isEqualTo("ana@ejemplo.com");
        assertThat(camposInvalidos(request)).doesNotContain("email");
    }

    @Test
    void elNombreSeRecortaConservandoMayusculasYEspaciosInternos() {
        RegistroRequest request = registro("ana@ejemplo.com", "secreta123", "  María José ");

        assertThat(request.nombre()).isEqualTo("María José");
    }

    @Test
    void unNombreQueSoloCumpleLaLongitudPorLosEspaciosSeRechaza() {
        RegistroRequest request = registro("ana@ejemplo.com", "secreta123", "  A  ");

        assertThat(camposInvalidos(request)).contains("nombre");
    }

    @Test
    void laContrasenaSeConservaIdentica() {
        RegistroRequest request = registro("ana@ejemplo.com", " secreta123 ", "Ana");

        assertThat(request.contrasena()).isEqualTo(" secreta123 ");
    }

    @Test
    void unEmailNuloSeRechazaConNotBlankSinLanzarExcepcion() {
        RegistroRequest request = registro(null, "secreta123", "Ana");

        assertThat(request.email()).isNull();
        assertThat(camposInvalidos(request)).contains("email");
    }

    @Test
    void unRegistroValidoNoTieneErrores() {
        RegistroRequest request = registro("ana@ejemplo.com", "secreta123", "Ana");

        assertThat(camposInvalidos(request)).isEmpty();
        assertThat(request.monedaPredeterminada()).isEqualTo("BOB");
        assertThat(request.telefono()).isNull();
    }

    private static RegistroRequest registro(String email, String contrasena, String nombre) {
        return new RegistroRequest(
                email, contrasena, nombre, "Rojas", LocalDate.parse("1990-05-20"), "  ", null);
    }

    private static Set<String> camposInvalidos(RegistroRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
