package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class MoverCuentaRequestTest {

    @Test
    void laCuentaEsObligatoria() {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            Validator validator = fabrica.getValidator();

            assertThat(validator.validate(new MoverCuentaRequest(null))).hasSize(1);
            assertThat(validator.validate(new MoverCuentaRequest(5L))).isEmpty();
        }
    }
}
