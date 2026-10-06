package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MoverGrupoCategoriaRequestTest {

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
    void ceroYUnaPosicionPositivaSonValidas() {
        assertThat(validator.validate(new MoverGrupoCategoriaRequest(0))).isEmpty();
        assertThat(validator.validate(new MoverGrupoCategoriaRequest(7))).isEmpty();
    }

    @Test
    void unaPosicionNulaONegativaSeRechaza() {
        assertThat(validator.validate(new MoverGrupoCategoriaRequest(null))).isNotEmpty();
        assertThat(validator.validate(new MoverGrupoCategoriaRequest(-1))).isNotEmpty();
    }
}
