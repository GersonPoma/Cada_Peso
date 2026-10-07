package com.presupuesto.beneficiario.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarBeneficiarioRequestTest {

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
        assertThat(new ActualizarBeneficiarioRequest("  Spotify ", 3L).nombre())
                .isEqualTo("Spotify");
    }

    @Test
    void laCategoriaPuedeSerNulaParaQuitarla() {
        ActualizarBeneficiarioRequest request = new ActualizarBeneficiarioRequest("Spotify", null);

        assertThat(request.categoriaId()).isNull();
        assertThat(camposInvalidos(request)).isEmpty();
    }

    @Test
    void unNombreVacioSoloEspaciosONuloSeRechaza() {
        assertThat(camposInvalidos(new ActualizarBeneficiarioRequest("", null)))
                .contains("nombre");
        assertThat(camposInvalidos(new ActualizarBeneficiarioRequest("  ", null)))
                .contains("nombre");
        assertThat(camposInvalidos(new ActualizarBeneficiarioRequest(null, null)))
                .contains("nombre");
    }

    @Test
    void elLimiteDelNombreSeAplicaSobreElValorRecortado() {
        assertThat(camposInvalidos(
                new ActualizarBeneficiarioRequest(" " + "a".repeat(100) + " ", null))).isEmpty();
        assertThat(camposInvalidos(new ActualizarBeneficiarioRequest("a".repeat(101), null)))
                .contains("nombre");
    }

    private static Set<String> camposInvalidos(ActualizarBeneficiarioRequest request) {
        return validator.validate(request).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
