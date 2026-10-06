package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarCategoriaRequestTest {

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
    void elNombreYLaNotaSeRecortan() {
        ActualizarCategoriaRequest request = new ActualizarCategoriaRequest("  Luz  ", "  Mes  ");

        assertThat(request.nombre()).isEqualTo("Luz");
        assertThat(request.nota()).isEqualTo("Mes");
    }

    @Test
    void unaNotaVaciaOEnBlancoQuedaNula() {
        assertThat(new ActualizarCategoriaRequest("Luz", "").nota()).isNull();
        assertThat(new ActualizarCategoriaRequest("Luz", "   ").nota()).isNull();
        assertThat(new ActualizarCategoriaRequest("Luz", null).nota()).isNull();
    }

    @Test
    void unNombreNuloVacioOEnBlancoSeRechaza() {
        assertThat(validator.validate(new ActualizarCategoriaRequest(null, null))).isNotEmpty();
        assertThat(validator.validate(new ActualizarCategoriaRequest("", null))).isNotEmpty();
        assertThat(validator.validate(new ActualizarCategoriaRequest("  ", null))).isNotEmpty();
    }

    @Test
    void ellimiteDelNombreYDeLaNota() {
        assertThat(validator.validate(new ActualizarCategoriaRequest("a".repeat(100), "n")))
                .isEmpty();
        assertThat(validator.validate(new ActualizarCategoriaRequest("a".repeat(101), null)))
                .isNotEmpty();
        assertThat(validator.validate(new ActualizarCategoriaRequest("Luz", "n".repeat(500))))
                .isEmpty();
        assertThat(validator.validate(new ActualizarCategoriaRequest("Luz", "n".repeat(501))))
                .isNotEmpty();
    }
}
