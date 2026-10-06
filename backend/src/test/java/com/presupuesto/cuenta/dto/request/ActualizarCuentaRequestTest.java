package com.presupuesto.cuenta.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.TipoCuenta;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarCuentaRequestTest {

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
    void elNombreSeRecorta() {
        assertThat(new ActualizarCuentaRequest("  Banco  ", TipoCuenta.AHORRO).nombre())
                .isEqualTo("Banco");
    }

    @Test
    void unaEdicionValidaNoTieneErrores() {
        assertThat(camposInvalidos(new ActualizarCuentaRequest("Banco", TipoCuenta.AHORRO)))
                .isEmpty();
    }

    @Test
    void unNombreVacioONuloSeRechaza() {
        assertThat(camposInvalidos(new ActualizarCuentaRequest("  ", TipoCuenta.AHORRO)))
                .contains("nombre");
        assertThat(camposInvalidos(new ActualizarCuentaRequest(null, TipoCuenta.AHORRO)))
                .contains("nombre");
    }

    @Test
    void unNombreDeMasDeCienCaracteresSeRechaza() {
        assertThat(camposInvalidos(
                new ActualizarCuentaRequest("a".repeat(101), TipoCuenta.AHORRO)))
                .contains("nombre");
    }

    @Test
    void unTipoNuloSeRechaza() {
        assertThat(camposInvalidos(new ActualizarCuentaRequest("Banco", null)))
                .contains("tipo");
    }

    private static Set<String> camposInvalidos(ActualizarCuentaRequest request) {
        return validator.validate(request).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
