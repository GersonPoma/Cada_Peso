package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarGrupoCategoriaRequestTest {

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
        assertThat(new ActualizarGrupoCategoriaRequest("  Hogar  ").nombre())
                .isEqualTo("Hogar");
    }

    @Test
    void unNombreNuloVacioOEnBlancoSeRechaza() {
        assertThat(validator.validate(new ActualizarGrupoCategoriaRequest(null))).isNotEmpty();
        assertThat(validator.validate(new ActualizarGrupoCategoriaRequest(""))).isNotEmpty();
        assertThat(validator.validate(new ActualizarGrupoCategoriaRequest("   "))).isNotEmpty();
    }

    @Test
    void ellimiteDeCienCaracteres() {
        assertThat(validator.validate(new ActualizarGrupoCategoriaRequest("a".repeat(100))))
                .isEmpty();
        assertThat(validator.validate(new ActualizarGrupoCategoriaRequest("a".repeat(101))))
                .isNotEmpty();
    }
}
