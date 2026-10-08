package com.presupuesto.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.comun.validacion.MaximoBytesUtf8;
import com.presupuesto.usuario.validacion.MayorDeEdadValidator;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @Test
    void laContrasenaVaciaOSoloDeEspaciosSeRechazaConNotBlank() {
        assertThat(violacionesDeContrasena("        ")).containsExactly(NotBlank.class);
        assertThat(violacionesDeContrasena(null)).containsExactly(NotBlank.class);
        assertThat(violacionesDeContrasena("")).contains(NotBlank.class);
    }

    @Test
    void laContrasenaDeSieteCaracteresSeRechazaConSize() {
        assertThat(violacionesDeContrasena("corta12")).containsExactly(Size.class);
    }

    @Test
    void laContrasenaDeOchoCaracteresSeAcepta() {
        assertThat(violacionesDeContrasena("12345678")).isEmpty();
    }

    @Test
    void laContrasenaDe72CaracteresSeAcepta() {
        assertThat(violacionesDeContrasena("a".repeat(72))).isEmpty();
    }

    @Test
    void laContrasenaDe73CaracteresSeRechazaPorLongitudYPorBytes() {
        assertThat(violacionesDeContrasena("a".repeat(73)))
                .containsExactlyInAnyOrder(Size.class, MaximoBytesUtf8.class);
    }

    @Test
    void laContrasenaDe40CaracteresDeDosBytesSeRechazaSoloPorBytes() {
        String contrasena = "ñ".repeat(40);

        assertThat(contrasena).hasSize(40);
        assertThat(violacionesDeContrasena(contrasena)).containsExactly(MaximoBytesUtf8.class);
    }

    @Test
    void laContrasenaDe36CaracteresDeDosBytesOcupaExactamente72BytesYSeAcepta() {
        assertThat(violacionesDeContrasena("ñ".repeat(36))).isEmpty();
    }

    @Test
    void elErrorDeContrasenaViajaEnElCampoContrasenaConSuMensajePorDefecto() {
        Set<ConstraintViolation<RegistroRequest>> violaciones =
                validator.validate(registro("ana@ejemplo.com", "corta12", "Ana"));

        assertThat(violaciones)
                .singleElement()
                .satisfies(violacion -> {
                    assertThat(violacion.getPropertyPath().toString()).isEqualTo("contrasena");
                    assertThat(violacion.getMessage()).isEqualTo(mensajePorDefecto(Size.class));
                });
    }

    private static Set<Class<?>> violacionesDeContrasena(String contrasena) {
        return validator.validate(registro("ana@ejemplo.com", contrasena, "Ana")).stream()
                .filter(violacion -> violacion.getPropertyPath().toString().equals("contrasena"))
                .map(violacion -> (Class<?>) violacion.getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType())
                .collect(Collectors.toSet());
    }

    /** Mensaje que Hibernate Validator da hoy a {@code @Size(min = 8, max = 72)} en esta JVM. */
    private static String mensajePorDefecto(Class<?> anotacion) {
        return validator.validate(new Referencia("corta12")).stream()
                .filter(violacion -> violacion.getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType()
                        .equals(anotacion))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElseThrow();
    }

    private record Referencia(@Size(min = 8, max = 72) String contrasena) {}

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
