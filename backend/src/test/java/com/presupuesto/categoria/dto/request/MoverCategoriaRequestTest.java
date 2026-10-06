package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MoverCategoriaRequestTest {

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
    void unMovimientoCompletoEsValido() {
        assertThat(validator.validate(new MoverCategoriaRequest(1L, 0))).isEmpty();
    }

    @Test
    void sinGrupoSeRechaza() {
        assertThat(validator.validate(new MoverCategoriaRequest(null, 0))).isNotEmpty();
    }

    @Test
    void unaPosicionNulaONegativaSeRechaza() {
        assertThat(validator.validate(new MoverCategoriaRequest(1L, null))).isNotEmpty();
        assertThat(validator.validate(new MoverCategoriaRequest(1L, -1))).isNotEmpty();
    }
}
