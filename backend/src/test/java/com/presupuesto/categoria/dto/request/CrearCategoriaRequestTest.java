package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrearCategoriaRequestTest {

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
        CrearCategoriaRequest request = new CrearCategoriaRequest(1L, "  Alquiler  ", "  Mes  ");

        assertThat(request.nombre()).isEqualTo("Alquiler");
        assertThat(request.nota()).isEqualTo("Mes");
    }

    @Test
    void unaNotaVaciaOEnBlancoQuedaNula() {
        assertThat(new CrearCategoriaRequest(1L, "Luz", "").nota()).isNull();
        assertThat(new CrearCategoriaRequest(1L, "Luz", "   ").nota()).isNull();
        assertThat(new CrearCategoriaRequest(1L, "Luz", null).nota()).isNull();
    }

    @Test
    void unaCategoriaValidaNoTieneErrores() {
        assertThat(validator.validate(new CrearCategoriaRequest(1L, "Luz", null))).isEmpty();
    }

    @Test
    void sinGrupoSeRechaza() {
        assertThat(validator.validate(new CrearCategoriaRequest(null, "Luz", null)))
                .isNotEmpty();
    }

    @Test
    void unNombreNuloVacioOEnBlancoSeRechaza() {
        assertThat(validator.validate(new CrearCategoriaRequest(1L, null, null))).isNotEmpty();
        assertThat(validator.validate(new CrearCategoriaRequest(1L, "", null))).isNotEmpty();
        assertThat(validator.validate(new CrearCategoriaRequest(1L, "  ", null))).isNotEmpty();
    }

    @Test
    void ellimiteDelNombreEsDeCienCaracteres() {
        assertThat(validator.validate(new CrearCategoriaRequest(1L, "a".repeat(100), null)))
                .isEmpty();
        assertThat(validator.validate(new CrearCategoriaRequest(1L, "a".repeat(101), null)))
                .isNotEmpty();
    }

    @Test
    void ellimiteDeLaNotaEsDeQuinientosCaracteresSobreElValorRecortado() {
        assertThat(validator.validate(
                new CrearCategoriaRequest(1L, "Luz", " " + "n".repeat(500) + " "))).isEmpty();
        assertThat(validator.validate(new CrearCategoriaRequest(1L, "Luz", "n".repeat(501))))
                .isNotEmpty();
    }
}
