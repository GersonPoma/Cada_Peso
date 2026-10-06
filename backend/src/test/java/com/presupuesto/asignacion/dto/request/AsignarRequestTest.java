package com.presupuesto.asignacion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class AsignarRequestTest {

    @Test
    void elAsignadoEsObligatorioYAdmiteCeroYNegativos() {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            Validator validator = fabrica.getValidator();

            assertThat(validator.validate(new AsignarRequest(null))).hasSize(1);
            assertThat(validator.validate(new AsignarRequest(100_000L))).isEmpty();
            assertThat(validator.validate(new AsignarRequest(0L))).isEmpty();
            assertThat(validator.validate(new AsignarRequest(-5_000L))).isEmpty();
        }
    }
}
