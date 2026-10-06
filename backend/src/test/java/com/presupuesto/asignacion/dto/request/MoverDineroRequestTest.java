package com.presupuesto.asignacion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MoverDineroRequestTest {

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
    void unMovimientoValidoNoTieneErrores() {
        assertThat(campos(new MoverDineroRequest(1L, 2L, 1L))).isEmpty();
    }

    @Test
    void losIdsYElMontoSonObligatorios() {
        assertThat(campos(new MoverDineroRequest(null, 2L, 1L))).containsExactly("origenId");
        assertThat(campos(new MoverDineroRequest(1L, null, 1L))).containsExactly("destinoId");
        assertThat(campos(new MoverDineroRequest(1L, 2L, null))).containsExactly("monto");
    }

    @Test
    void elMontoDebeSerMayorQueCero() {
        assertThat(campos(new MoverDineroRequest(1L, 2L, 0L))).containsExactly("monto");
        assertThat(campos(new MoverDineroRequest(1L, 2L, -100L))).containsExactly("monto");
    }

    private static Set<String> campos(MoverDineroRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
