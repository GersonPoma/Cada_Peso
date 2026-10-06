package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrearGrupoCategoriaRequestTest {

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
    void elNombreSeRecortaConservandoLosEspaciosInternos() {
        assertThat(new CrearGrupoCategoriaRequest("  Vida Diaria  ").nombre())
                .isEqualTo("Vida Diaria");
    }

    @Test
    void unNombreValidoNoTieneErrores() {
        assertThat(validator.validate(new CrearGrupoCategoriaRequest("Vivienda"))).isEmpty();
    }

    @Test
    void unNombreNuloVacioOEnBlancoSeRechaza() {
        assertThat(validator.validate(new CrearGrupoCategoriaRequest(null))).isNotEmpty();
        assertThat(validator.validate(new CrearGrupoCategoriaRequest(""))).isNotEmpty();
        assertThat(validator.validate(new CrearGrupoCategoriaRequest("   "))).isNotEmpty();
    }

    @Test
    void ellimiteDeCienCaracteresSeAplicaSobreElValorRecortado() {
        assertThat(validator.validate(
                new CrearGrupoCategoriaRequest(" " + "a".repeat(100) + " "))).isEmpty();
        assertThat(validator.validate(new CrearGrupoCategoriaRequest("a".repeat(101))))
                .isNotEmpty();
    }
}
