package com.presupuesto.usuario.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.comun.validacion.MaximoBytesUtf8;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContrasenaValidaTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private record ConAnotacion(@ContrasenaValida String contrasena) {}

    /** Las tres anotaciones que tenía el registro antes de la anotación compuesta. */
    private record ConLasTresOriginales(
            @NotBlank @Size(min = 8, max = 72) @MaximoBytesUtf8(72) String contrasena) {}

    @BeforeAll
    static void crearValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void cerrarValidator() {
        validatorFactory.close();
    }

    @Test
    void aceptaUnaContrasenaDeOchoCaracteres() {
        assertThat(violaciones("12345678")).isEmpty();
    }

    @Test
    void aceptaUnaContrasenaDeExactamente72Caracteres() {
        assertThat(violaciones("a".repeat(72))).isEmpty();
    }

    @Test
    void aceptaUnaContrasenaDe36CaracteresDeDosBytes() {
        String contrasena = "ñ".repeat(36);

        assertThat(contrasena.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(violaciones(contrasena)).isEmpty();
    }

    @Test
    void conservaLosEspaciosSinRecortarlos() {
        assertThat(violaciones("  clave nueva 9 ")).isEmpty();
        assertThat(violaciones("  a    ")).containsExactly(Size.class);
    }

    @Test
    void rechazaUnaContrasenaNulaVaciaOSoloDeEspacios() {
        assertThat(violaciones(null)).containsExactly(NotBlank.class);
        assertThat(violaciones("")).contains(NotBlank.class);
        assertThat(violaciones("        ")).containsExactly(NotBlank.class);
    }

    @Test
    void rechazaUnaContrasenaDeSieteCaracteres() {
        assertThat(violaciones("corta12")).containsExactly(Size.class);
    }

    @Test
    void rechazaUnaContrasenaDe73Caracteres() {
        assertThat(violaciones("a".repeat(73)))
                .containsExactlyInAnyOrder(Size.class, MaximoBytesUtf8.class);
    }

    @Test
    void rechazaPorBytesUnaContrasenaDe40CaracteresDeDosBytes() {
        String contrasena = "ñ".repeat(40);

        assertThat(contrasena).hasSize(40);
        assertThat(violaciones(contrasena)).containsExactly(MaximoBytesUtf8.class);
    }

    @Test
    void rechazaPorBytesUnaContrasenaDe37CaracteresDeDosBytes() {
        assertThat(violaciones("ñ".repeat(37))).containsExactly(MaximoBytesUtf8.class);
    }

    @Test
    void reportaLosMismosCamposYMensajesQueLasTresAnotacionesOriginales() {
        for (String contrasena : new String[] {
            null, "", "        ", "corta12", "a".repeat(73), "ñ".repeat(37), "ñ".repeat(40),
            "a".repeat(72), "12345678"
        }) {
            assertThat(camposYMensajes(validator.validate(new ConAnotacion(contrasena))))
                    .as("contraseña de %s", contrasena == null ? "null" : contrasena.length())
                    .isEqualTo(camposYMensajes(
                            validator.validate(new ConLasTresOriginales(contrasena))));
        }
    }

    private static Set<Class<?>> violaciones(String contrasena) {
        return validator.validate(new ConAnotacion(contrasena)).stream()
                .map(violacion -> (Class<?>) violacion.getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType())
                .collect(Collectors.toSet());
    }

    private static Set<String> camposYMensajes(Set<? extends ConstraintViolation<?>> violaciones) {
        return violaciones.stream()
                .map(violacion -> violacion.getPropertyPath() + ": " + violacion.getMessage())
                .collect(Collectors.toSet());
    }
}
