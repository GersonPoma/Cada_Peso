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

class CrearBeneficiarioRequestTest {

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
    void elNombreSeRecortaConservandoMayusculasYEspaciosInternos() {
        assertThat(new CrearBeneficiarioRequest("  Tienda Don Pepe  ", null).nombre())
                .isEqualTo("Tienda Don Pepe");
    }

    @Test
    void unBeneficiarioValidoNoTieneErrores() {
        assertThat(camposInvalidos(new CrearBeneficiarioRequest("Netflix", 5L))).isEmpty();
        assertThat(camposInvalidos(new CrearBeneficiarioRequest("Netflix", null))).isEmpty();
    }

    @Test
    void unNombreVacioSoloEspaciosONuloSeRechaza() {
        assertThat(camposInvalidos(new CrearBeneficiarioRequest("", null))).contains("nombre");
        assertThat(camposInvalidos(new CrearBeneficiarioRequest("   ", null)))
                .contains("nombre");
        assertThat(camposInvalidos(new CrearBeneficiarioRequest(null, null))).contains("nombre");
    }

    @Test
    void elLimiteDelNombreSeAplicaSobreElValorRecortado() {
        assertThat(camposInvalidos(
                new CrearBeneficiarioRequest(" " + "a".repeat(100) + " ", null))).isEmpty();
        assertThat(camposInvalidos(new CrearBeneficiarioRequest("a".repeat(101), null)))
                .contains("nombre");
    }

    private static Set<String> camposInvalidos(CrearBeneficiarioRequest request) {
        return validator.validate(request).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
