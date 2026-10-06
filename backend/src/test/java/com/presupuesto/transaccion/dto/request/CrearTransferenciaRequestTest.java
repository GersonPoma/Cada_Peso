package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CrearTransferenciaRequestTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 1);

    private static int errores(CrearTransferenciaRequest request) {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            return fabrica.getValidator().validate(request).size();
        }
    }

    @Test
    void unRequestValidoNoTieneErrores() {
        assertThat(errores(new CrearTransferenciaRequest(1L, 2L, FECHA, 30000L, null, null)))
                .isZero();
    }

    @Test
    void montoCeroONegativoONuloEsInvalido() {
        assertThat(errores(new CrearTransferenciaRequest(1L, 2L, FECHA, 0L, null, null)))
                .isEqualTo(1);
        assertThat(errores(new CrearTransferenciaRequest(1L, 2L, FECHA, -5L, null, null)))
                .isEqualTo(1);
        assertThat(errores(new CrearTransferenciaRequest(1L, 2L, FECHA, null, null, null)))
                .isEqualTo(1);
    }

    @Test
    void cuentasYFechaSonObligatorias() {
        assertThat(errores(new CrearTransferenciaRequest(null, null, null, 1L, null, null)))
                .isEqualTo(3);
    }

    @Test
    void elMemoSeRecortaYVacioPasaANulo() {
        assertThat(new CrearTransferenciaRequest(1L, 2L, FECHA, 1L, null, "  nota  ").memo())
                .isEqualTo("nota");
        assertThat(new CrearTransferenciaRequest(1L, 2L, FECHA, 1L, null, "   ").memo()).isNull();
    }

    @Test
    void elMemoNoPuedeSuperar500Caracteres() {
        assertThat(errores(new CrearTransferenciaRequest(
                1L, 2L, FECHA, 1L, null, "a".repeat(501)))).isEqualTo(1);
    }
}
