package com.presupuesto.meta.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.meta.dto.request.GuardarMetaRequest;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.TipoMeta;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetaCoherenteValidatorTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 12, 15);

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

    private static GuardarMetaRequest request(
            TipoMeta tipo, FrecuenciaMeta frecuencia, Integer dia, Integer intervalo,
            LocalDate inicio, LocalDate objetivo) {
        return new GuardarMetaRequest(tipo, 1_000L, frecuencia, dia, intervalo, inicio, objetivo);
    }

    private static Map<String, String> errores(GuardarMetaRequest request) {
        Map<String, String> errores = new TreeMap<>();
        for (ConstraintViolation<GuardarMetaRequest> v : validator.validate(request)) {
            errores.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return errores;
    }

    @Test
    void unaMetaCompletaDeCadaTipoEsValida() {
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.MENSUAL,
                null, null, null, null))).isEmpty();
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.SEMANAL,
                1, null, null, null))).isEmpty();
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.PERSONALIZADA,
                null, 14, FECHA, null))).isEmpty();
        assertThat(errores(request(TipoMeta.MONTO_PARA_FECHA, null, null, null, null, FECHA)))
                .isEmpty();
        assertThat(errores(request(TipoMeta.SALDO_OBJETIVO, null, null, null, null, null)))
                .isEmpty();
    }

    @Test
    void montoMensualSinFrecuenciaMarcaLaFrecuencia() {
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, null, null, null, null, null)))
                .containsOnlyKeys("frecuencia")
                .containsEntry("frecuencia", MetaCoherenteValidator.FRECUENCIA_OBLIGATORIA);
    }

    @Test
    void semanalSinDiaMarcaElDia() {
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.SEMANAL,
                null, null, null, null))).containsOnlyKeys("diaSemana");
    }

    @Test
    void personalizadaSinIntervaloNiInicioMarcaAmbosCampos() {
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.PERSONALIZADA,
                null, null, null, null))).containsOnlyKeys("intervaloDias", "fechaInicio");
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.PERSONALIZADA,
                null, 14, null, null))).containsOnlyKeys("fechaInicio");
        assertThat(errores(request(TipoMeta.MONTO_MENSUAL, FrecuenciaMeta.PERSONALIZADA,
                null, null, FECHA, null))).containsOnlyKeys("intervaloDias");
    }

    @Test
    void montoParaFechaSinFechaObjetivoMarcaLaFecha() {
        assertThat(errores(request(TipoMeta.MONTO_PARA_FECHA, null, null, null, null, null)))
                .containsOnlyKeys("fechaObjetivo");
    }

    @Test
    void unaFechaObjetivoPasadaEsValida() {
        assertThat(errores(request(TipoMeta.MONTO_PARA_FECHA, null, null, null, null,
                LocalDate.of(2000, 1, 31)))).isEmpty();
    }

    @Test
    void sinTipoNoAgregaErroresPropios() {
        assertThat(errores(request(null, FrecuenciaMeta.SEMANAL, null, null, null, null)))
                .containsOnlyKeys("tipo");
    }

    @Test
    void aceptaNull() {
        assertThat(new MetaCoherenteValidator().isValid(null, null)).isTrue();
    }
}
