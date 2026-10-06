package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SubTransaccionRequestTest {

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
    void unaSubtransaccionValidaNoTieneErrores() {
        assertThat(campos(new SubTransaccionRequest(1L, -500L, "pan"))).isEmpty();
        assertThat(campos(new SubTransaccionRequest(null, 500L, null))).isEmpty();
    }

    @Test
    void montoCeroONuloSeRechaza() {
        assertThat(campos(new SubTransaccionRequest(null, 0L, null))).containsExactly("monto");
        assertThat(campos(new SubTransaccionRequest(null, null, null))).containsExactly("monto");
    }

    @Test
    void elMemoSeRecortaYElLimiteSeAplicaSobreElValorRecortado() {
        assertThat(new SubTransaccionRequest(null, 1L, "  hola  ").memo()).isEqualTo("hola");
        assertThat(new SubTransaccionRequest(null, 1L, "   ").memo()).isNull();
        assertThat(campos(new SubTransaccionRequest(null, 1L, " " + "a".repeat(500) + " ")))
                .isEmpty();
        assertThat(campos(new SubTransaccionRequest(null, 1L, "a".repeat(501))))
                .containsExactly("memo");
    }

    private static Set<String> campos(SubTransaccionRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
