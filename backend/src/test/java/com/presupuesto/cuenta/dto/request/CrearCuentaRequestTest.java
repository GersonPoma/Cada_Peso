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

class CrearCuentaRequestTest {

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
        assertThat(request("  Mi Banco  ", TipoCuenta.CORRIENTE).nombre()).isEqualTo("Mi Banco");
    }

    @Test
    void unaCuentaValidaNoTieneErrores() {
        assertThat(camposInvalidos(request("Banco", TipoCuenta.AHORRO))).isEmpty();
    }

    @Test
    void unNombreVacioSoloEspaciosONuloSeRechaza() {
        assertThat(camposInvalidos(request("   ", TipoCuenta.AHORRO))).contains("nombre");
        assertThat(camposInvalidos(request("", TipoCuenta.AHORRO))).contains("nombre");
        assertThat(camposInvalidos(request(null, TipoCuenta.AHORRO))).contains("nombre");
    }

    @Test
    void elLimiteDelNombreSeAplicaSobreElValorRecortado() {
        assertThat(camposInvalidos(request(" " + "a".repeat(100) + " ", TipoCuenta.AHORRO)))
                .isEmpty();
        assertThat(camposInvalidos(request("a".repeat(101), TipoCuenta.AHORRO)))
                .contains("nombre");
    }

    @Test
    void unTipoNuloSeRechaza() {
        assertThat(camposInvalidos(request("Banco", null))).contains("tipo");
    }

    @Test
    void sinEnPresupuestoNiSaldoUsaLosValoresPorDefecto() {
        CrearCuentaRequest request = request("Banco", TipoCuenta.AHORRO);

        assertThat(request.enPresupuesto()).isTrue();
        assertThat(request.saldoInicial()).isZero();
    }

    @Test
    void losValoresExplicitosSeConservan() {
        CrearCuentaRequest request =
                new CrearCuentaRequest("Fondo", TipoCuenta.INVERSION, false, -5L);

        assertThat(request.enPresupuesto()).isFalse();
        assertThat(request.saldoInicial()).isEqualTo(-5L);
    }

    private static CrearCuentaRequest request(String nombre, TipoCuenta tipo) {
        return new CrearCuentaRequest(nombre, tipo, null, null);
    }

    private static Set<String> camposInvalidos(CrearCuentaRequest request) {
        return validator.validate(request).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
