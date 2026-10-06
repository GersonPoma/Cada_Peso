package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.transaccion.entity.EstadoTransaccion;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class CambiarEstadoRequestTest {

    @Test
    void elEstadoEsObligatorio() {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            Validator validator = fabrica.getValidator();

            assertThat(validator.validate(new CambiarEstadoRequest(null))).hasSize(1);
            assertThat(validator.validate(new CambiarEstadoRequest(EstadoTransaccion.CONCILIADA)))
                    .isEmpty();
        }
    }
}
