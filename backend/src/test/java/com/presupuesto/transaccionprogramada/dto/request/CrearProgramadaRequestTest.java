package com.presupuesto.transaccionprogramada.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrearProgramadaRequestTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 5);

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

    private static CrearProgramadaRequest request(
            Long cuenta, LocalDate inicio, FrecuenciaProgramada frecuencia, Long monto,
            String beneficiario, String memo) {
        return new CrearProgramadaRequest(
                cuenta, inicio, frecuencia, null, monto, null, beneficiario, memo);
    }

    private static Set<String> campos(CrearProgramadaRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void unaPlantillaMinimaEsValida() {
        assertThat(campos(request(1L, INICIO, FrecuenciaProgramada.MENSUAL, -1000L, null, null)))
                .isEmpty();
    }

    @Test
    void cuentaInicioFrecuenciaYMontoSonObligatorios() {
        assertThat(campos(request(null, INICIO, FrecuenciaProgramada.MENSUAL, 1L, null, null)))
                .containsExactly("cuentaId");
        assertThat(campos(request(1L, null, FrecuenciaProgramada.MENSUAL, 1L, null, null)))
                .containsExactly("fechaInicio");
        assertThat(campos(request(1L, INICIO, null, 1L, null, null)))
                .containsExactly("frecuencia");
        assertThat(campos(request(1L, INICIO, FrecuenciaProgramada.MENSUAL, null, null, null)))
                .containsExactly("monto");
    }

    @Test
    void elMontoCeroEsInvalido() {
        assertThat(campos(request(1L, INICIO, FrecuenciaProgramada.MENSUAL, 0L, null, null)))
                .containsExactly("monto");
    }

    @Test
    void beneficiarioYMemoSeRecortanYLosVaciosPasanANulo() {
        CrearProgramadaRequest request = request(
                1L, INICIO, FrecuenciaProgramada.MENSUAL, 1L, "  Casero  ", "   ");

        assertThat(request.beneficiario()).isEqualTo("Casero");
        assertThat(request.memo()).isNull();
    }

    @Test
    void respetaLosLimitesDe100Y500Caracteres() {
        assertThat(campos(request(1L, INICIO, FrecuenciaProgramada.MENSUAL, 1L,
                "b".repeat(100), "m".repeat(500)))).isEmpty();
        assertThat(campos(request(1L, INICIO, FrecuenciaProgramada.MENSUAL, 1L,
                "b".repeat(101), null))).containsExactly("beneficiario");
        assertThat(campos(request(1L, INICIO, FrecuenciaProgramada.MENSUAL, 1L,
                null, "m".repeat(501)))).containsExactly("memo");
    }
}
