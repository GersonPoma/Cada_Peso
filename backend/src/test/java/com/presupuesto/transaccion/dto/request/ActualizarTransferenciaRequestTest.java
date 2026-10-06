package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ActualizarTransferenciaRequestTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 1);

    private static int errores(ActualizarTransferenciaRequest request) {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            return fabrica.getValidator().validate(request).size();
        }
    }

    @Test
    void unRequestValidoNoTieneErrores() {
        assertThat(errores(new ActualizarTransferenciaRequest(FECHA, 45000L, 3L, "x"))).isZero();
    }

    @Test
    void montoCeroNegativoONuloYFechaNulaSonInvalidos() {
        assertThat(errores(new ActualizarTransferenciaRequest(FECHA, 0L, null, null))).isEqualTo(1);
        assertThat(errores(new ActualizarTransferenciaRequest(FECHA, -1L, null, null)))
                .isEqualTo(1);
        assertThat(errores(new ActualizarTransferenciaRequest(null, null, null, null)))
                .isEqualTo(2);
    }

    @Test
    void elMemoSeRecortaYVacioPasaANulo() {
        assertThat(new ActualizarTransferenciaRequest(FECHA, 1L, null, " hola ").memo())
                .isEqualTo("hola");
        assertThat(new ActualizarTransferenciaRequest(FECHA, 1L, null, "").memo()).isNull();
    }
}
