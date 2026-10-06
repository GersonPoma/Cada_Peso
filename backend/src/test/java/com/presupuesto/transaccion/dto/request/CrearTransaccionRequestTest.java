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

class CrearTransaccionRequestTest {

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
    void unaTransaccionMinimaEsValidaYTomaLosValoresPorDefecto() {
        CrearTransaccionRequest request = request(1L, FECHA, -2500L, null, null);

        assertThat(campos(request)).isEmpty();
        assertThat(request.aprobada()).isTrue();
        assertThat(request.subtransacciones()).isEmpty();
        assertThat(request.beneficiario()).isNull();
        assertThat(request.memo()).isNull();
    }

    @Test
    void aprobadaExplicitaSeConserva() {
        CrearTransaccionRequest request = new CrearTransaccionRequest(
                1L, FECHA, 1L, null, null, null, false, null);

        assertThat(request.aprobada()).isFalse();
    }

    @Test
    void cuentaFechaYMontoSonObligatorios() {
        assertThat(campos(request(null, FECHA, 1L, null, null))).containsExactly("cuentaId");
        assertThat(campos(request(1L, null, 1L, null, null))).containsExactly("fecha");
        assertThat(campos(request(1L, FECHA, null, null, null))).containsExactly("monto");
    }

    @Test
    void montoCeroSeRechaza() {
        assertThat(campos(request(1L, FECHA, 0L, null, null))).containsExactly("monto");
    }

    @Test
    void entradasYSalidasSonValidas() {
        assertThat(campos(request(1L, FECHA, 1L, null, null))).isEmpty();
        assertThat(campos(request(1L, FECHA, -1L, null, null))).isEmpty();
    }

    @Test
    void beneficiarioYMemoSeRecortanYVacioPasaANulo() {
        CrearTransaccionRequest request = request(1L, FECHA, 1L, "  Tienda  ", "   ");

        assertThat(request.beneficiario()).isEqualTo("Tienda");
        assertThat(request.memo()).isNull();
    }

    @Test
    void loslimitesDeLongitudSeAplicanSobreElValorRecortado() {
        assertThat(campos(request(1L, FECHA, 1L, " " + "a".repeat(100) + " ", null))).isEmpty();
        assertThat(campos(request(1L, FECHA, 1L, "a".repeat(101), null)))
                .containsExactly("beneficiario");
        assertThat(campos(request(1L, FECHA, 1L, null, "a".repeat(500)))).isEmpty();
        assertThat(campos(request(1L, FECHA, 1L, null, "a".repeat(501))))
                .containsExactly("memo");
    }

    @Test
    void lasSubtransaccionesSeValidanEnCascada() {
        CrearTransaccionRequest request = new CrearTransaccionRequest(
                1L, FECHA, -3L, null, null, null, null,
                List.of(new SubTransaccionRequest(null, -1L, null),
                        new SubTransaccionRequest(null, 0L, null)));

        assertThat(campos(request)).containsExactly("subtransacciones[1].monto");
    }

    private static CrearTransaccionRequest request(
            Long cuentaId, LocalDate fecha, Long monto, String beneficiario, String memo) {
        return new CrearTransaccionRequest(
                cuentaId, fecha, monto, null, beneficiario, memo, null, null);
    }

    private static Set<String> campos(CrearTransaccionRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
