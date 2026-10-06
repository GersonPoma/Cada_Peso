package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarTransaccionRequestTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 2);

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
    void unaEdicionValidaNoTieneErroresYSinSubtransaccionesQuedaVacia() {
        ActualizarTransaccionRequest request =
                new ActualizarTransaccionRequest(FECHA, -1L, null, "  Tienda ", "  ", null);

        assertThat(campos(request)).isEmpty();
        assertThat(request.subtransacciones()).isEmpty();
        assertThat(request.beneficiario()).isEqualTo("Tienda");
        assertThat(request.memo()).isNull();
    }

    @Test
    void fechaYMontoSonObligatoriosYElMontoNoPuedeSerCero() {
        assertThat(campos(new ActualizarTransaccionRequest(null, 1L, null, null, null, null)))
                .containsExactly("fecha");
        assertThat(campos(new ActualizarTransaccionRequest(FECHA, null, null, null, null, null)))
                .containsExactly("monto");
        assertThat(campos(new ActualizarTransaccionRequest(FECHA, 0L, null, null, null, null)))
                .containsExactly("monto");
    }

    @Test
    void loslimitesDeLongitudSeAplican() {
        assertThat(campos(new ActualizarTransaccionRequest(
                        FECHA, 1L, null, "a".repeat(101), "a".repeat(501), null)))
                .containsExactlyInAnyOrder("beneficiario", "memo");
    }

    @Test
    void lasSubtransaccionesSeValidanEnCascada() {
        ActualizarTransaccionRequest request = new ActualizarTransaccionRequest(
                FECHA, -3L, null, null, null,
                List.of(new SubTransaccionRequest(null, 0L, "a".repeat(501))));

        assertThat(campos(request))
                .containsExactlyInAnyOrder("subtransacciones[0].monto", "subtransacciones[0].memo");
    }

    private static Set<String> campos(ActualizarTransaccionRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
