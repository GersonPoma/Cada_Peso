package com.presupuesto.transaccionprogramada.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarProgramadaRequestTest {

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

    private static Set<String> campos(ActualizarProgramadaRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void montoYFrecuenciaSonObligatoriosYElMontoNoEsCero() {
        assertThat(campos(new ActualizarProgramadaRequest(
                -1L, null, null, null, FrecuenciaProgramada.SEMANAL, null))).isEmpty();
        assertThat(campos(new ActualizarProgramadaRequest(
                null, null, null, null, FrecuenciaProgramada.SEMANAL, null)))
                .containsExactly("monto");
        assertThat(campos(new ActualizarProgramadaRequest(0L, null, null, null,
                FrecuenciaProgramada.SEMANAL, null))).containsExactly("monto");
        assertThat(campos(new ActualizarProgramadaRequest(1L, null, null, null, null, null)))
                .containsExactly("frecuencia");
    }

    @Test
    void recortaTextoYRespetaLosLimites() {
        ActualizarProgramadaRequest request = new ActualizarProgramadaRequest(
                1L, null, "  Tienda ", "  ", FrecuenciaProgramada.DIARIA, null);

        assertThat(request.beneficiario()).isEqualTo("Tienda");
        assertThat(request.memo()).isNull();
        assertThat(campos(new ActualizarProgramadaRequest(
                1L, null, "b".repeat(101), "m".repeat(501), FrecuenciaProgramada.DIARIA, null)))
                .containsExactlyInAnyOrder("beneficiario", "memo");
    }
}
