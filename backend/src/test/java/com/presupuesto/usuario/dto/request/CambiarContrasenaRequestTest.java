package com.presupuesto.usuario.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CambiarContrasenaRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

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
    void unRequestValidoNoTieneErrores() {
        assertThat(camposInvalidos("secreta123", "otraClave456")).isEmpty();
    }

    @Test
    void ningunaContrasenaSeModifica() {
        CambiarContrasenaRequest request =
                new CambiarContrasenaRequest("  secreta123 ", "  clave nueva 9 ");

        assertThat(request.contrasenaActual()).isEqualTo("  secreta123 ");
        assertThat(request.contrasenaNueva()).isEqualTo("  clave nueva 9 ");
        assertThat(camposInvalidos("  secreta123 ", "  clave nueva 9 ")).isEmpty();
    }

    @Test
    void faltantesSeRechazanEnSuPropioCampo() {
        assertThat(camposInvalidos(null, "otraClave456")).containsExactly("contrasenaActual");
        assertThat(camposInvalidos("secreta123", null)).containsExactly("contrasenaNueva");
    }

    @Test
    void vaciasSeRechazan() {
        assertThat(camposInvalidos("", "otraClave456")).containsExactly("contrasenaActual");
        assertThat(camposInvalidos("        ", "otraClave456"))
                .containsExactly("contrasenaActual");
        assertThat(camposInvalidos("secreta123", "")).containsExactly("contrasenaNueva");
    }

    @Test
    void laActualNoTieneLimiteDeLongitudEnLaValidacion() {
        assertThat(camposInvalidos("a", "otraClave456")).isEmpty();
        assertThat(camposInvalidos("ñ".repeat(37), "otraClave456")).isEmpty();
    }

    @Test
    void limitesDeLaNueva() {
        assertThat(camposInvalidos("secreta123", "corta12")).containsExactly("contrasenaNueva");
        assertThat(camposInvalidos("secreta123", "12345678")).isEmpty();
        assertThat(camposInvalidos("secreta123", "a".repeat(72))).isEmpty();
        assertThat(camposInvalidos("secreta123", "a".repeat(73)))
                .containsExactly("contrasenaNueva");
    }

    @Test
    void laNuevaSeRechazaPorBytesAunqueTengaMenosDe72Caracteres() {
        assertThat(camposInvalidos("secreta123", "ñ".repeat(36))).isEmpty();
        assertThat(camposInvalidos("secreta123", "ñ".repeat(37)))
                .containsExactly("contrasenaNueva");
        assertThat(camposInvalidos("secreta123", "ñ".repeat(40)))
                .containsExactly("contrasenaNueva");
    }

    @Test
    void toStringNoContieneNingunaContrasena() {
        String texto = new CambiarContrasenaRequest("actualSecreta1", "nuevaSecreta2").toString();

        assertThat(texto)
                .doesNotContain("actualSecreta1")
                .doesNotContain("nuevaSecreta2")
                .contains("CambiarContrasenaRequest");
    }

    private static Set<String> camposInvalidos(String actual, String nueva) {
        return validator.validate(new CambiarContrasenaRequest(actual, nueva)).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
