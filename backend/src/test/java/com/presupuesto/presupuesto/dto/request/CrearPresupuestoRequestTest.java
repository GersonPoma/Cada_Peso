package com.presupuesto.presupuesto.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrearPresupuestoRequestTest {

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
        assertThat(request("  Mi Casa  ").nombre()).isEqualTo("Mi Casa");
    }

    @Test
    void unNombreValidoNoTieneErrores() {
        assertThat(camposInvalidos(request("Casa"))).isEmpty();
    }

    @Test
    void unNombreVacioOSoloEspaciosSeRechaza() {
        assertThat(camposInvalidos(request("   "))).contains("nombre");
        assertThat(camposInvalidos(request(""))).contains("nombre");
    }

    @Test
    void unNombreNuloSeRechazaSinLanzarExcepcion() {
        assertThat(camposInvalidos(request(null))).contains("nombre");
    }

    @Test
    void unNombreDe100CaracteresEsValidoYElDe101SeRechaza() {
        assertThat(camposInvalidos(request("a".repeat(100)))).isEmpty();
        assertThat(camposInvalidos(request("a".repeat(101)))).contains("nombre");
    }

    @Test
    void losEspaciosNoCuentanParaLaLongitudMaxima() {
        assertThat(camposInvalidos(request("  " + "a".repeat(100) + "  "))).isEmpty();
    }

    @Test
    void laMonedaEsOpcional() {
        assertThat(request("Casa").moneda()).isNull();
        assertThat(camposInvalidos(request("Casa"))).isEmpty();
    }

    @Test
    void unaMonedaExistenteEnMayusculasEsValida() {
        assertThat(camposInvalidos(new CrearPresupuestoRequest("Casa", "USD"))).isEmpty();
    }

    @Test
    void unaMonedaInexistenteOEnMinusculasSeRechaza() {
        assertThat(camposInvalidos(new CrearPresupuestoRequest("Casa", "ZZZ")))
                .containsExactly("moneda");
        assertThat(camposInvalidos(new CrearPresupuestoRequest("Casa", "usd")))
                .containsExactly("moneda");
    }

    private static CrearPresupuestoRequest request(String nombre) {
        return new CrearPresupuestoRequest(nombre, null);
    }

    private static Set<String> camposInvalidos(CrearPresupuestoRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
