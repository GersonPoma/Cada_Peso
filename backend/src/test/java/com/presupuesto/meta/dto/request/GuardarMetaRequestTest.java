package com.presupuesto.meta.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.TipoMeta;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuardarMetaRequestTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 2);
    private static final LocalDate OBJETIVO = LocalDate.of(2026, 12, 15);

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

    private static Set<String> campos(GuardarMetaRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    // ---------- tipo y monto ----------

    @Test
    void elTipoYElMontoSonObligatorios() {
        assertThat(campos(new GuardarMetaRequest(null, 1L, null, null, null, null, null)))
                .containsExactly("tipo");
        assertThat(campos(new GuardarMetaRequest(
                TipoMeta.SALDO_OBJETIVO, null, null, null, null, null, null)))
                .containsExactly("monto");
    }

    @Test
    void elMontoDebeSerMayorQueCeroEnTodosLosTipos() {
        for (TipoMeta tipo : TipoMeta.values()) {
            FrecuenciaMeta frecuencia =
                    tipo == TipoMeta.MONTO_MENSUAL ? FrecuenciaMeta.MENSUAL : null;
            LocalDate objetivo = tipo == TipoMeta.MONTO_PARA_FECHA ? OBJETIVO : null;
            assertThat(campos(new GuardarMetaRequest(
                    tipo, 0L, frecuencia, null, null, null, objetivo))).containsExactly("monto");
            assertThat(campos(new GuardarMetaRequest(
                    tipo, -5L, frecuencia, null, null, null, objetivo))).containsExactly("monto");
        }
    }

    // ---------- rangos ----------

    @Test
    void elDiaDeLaSemanaVaDeUnoASiete() {
        for (int dia : new int[] {1, 7}) {
            assertThat(campos(semanal(dia))).isEmpty();
        }
        for (int dia : new int[] {0, 8, -1}) {
            assertThat(campos(semanal(dia))).containsExactly("diaSemana");
        }
    }

    @Test
    void elIntervaloVaDeDosATrescientosSesentaYCinco() {
        for (int dias : new int[] {2, 365}) {
            assertThat(campos(personalizada(dias, INICIO))).isEmpty();
        }
        for (int dias : new int[] {1, 366, 0}) {
            assertThat(campos(personalizada(dias, INICIO))).containsExactly("intervaloDias");
        }
    }

    // ---------- faltantes por tipo ----------

    @Test
    void faltanLosDatosDeCadaTipo() {
        assertThat(campos(new GuardarMetaRequest(
                TipoMeta.MONTO_MENSUAL, 1L, null, null, null, null, null)))
                .containsExactly("frecuencia");
        assertThat(campos(new GuardarMetaRequest(
                TipoMeta.MONTO_MENSUAL, 1L, FrecuenciaMeta.SEMANAL, null, null, null, null)))
                .containsExactly("diaSemana");
        assertThat(campos(personalizada(null, null)))
                .containsExactlyInAnyOrder("intervaloDias", "fechaInicio");
        assertThat(campos(new GuardarMetaRequest(
                TipoMeta.MONTO_PARA_FECHA, 1L, null, null, null, null, null)))
                .containsExactly("fechaObjetivo");
    }

    // ---------- descarte de lo que no aplica ----------

    @Test
    void saldoObjetivoDescartaTodoLoQueNoEsElMonto() {
        GuardarMetaRequest request = new GuardarMetaRequest(TipoMeta.SALDO_OBJETIVO, 300_000L,
                FrecuenciaMeta.SEMANAL, 9, 400, INICIO, OBJETIVO);

        assertThat(request.frecuencia()).isNull();
        assertThat(request.diaSemana()).isNull();
        assertThat(request.intervaloDias()).isNull();
        assertThat(request.fechaInicio()).isNull();
        assertThat(request.fechaObjetivo()).isNull();
        assertThat(campos(request)).isEmpty();
    }

    @Test
    void montoParaFechaSoloConservaLaFechaObjetivo() {
        GuardarMetaRequest request = new GuardarMetaRequest(TipoMeta.MONTO_PARA_FECHA, 1L,
                FrecuenciaMeta.PERSONALIZADA, 3, 14, INICIO, OBJETIVO);

        assertThat(request.frecuencia()).isNull();
        assertThat(request.diaSemana()).isNull();
        assertThat(request.intervaloDias()).isNull();
        assertThat(request.fechaInicio()).isNull();
        assertThat(request.fechaObjetivo()).isEqualTo(OBJETIVO);
    }

    @Test
    void mensualDescartaDiaIntervaloYFechas() {
        GuardarMetaRequest request = new GuardarMetaRequest(TipoMeta.MONTO_MENSUAL, 1L,
                FrecuenciaMeta.MENSUAL, 3, 14, INICIO, OBJETIVO);

        assertThat(request.frecuencia()).isEqualTo(FrecuenciaMeta.MENSUAL);
        assertThat(request.diaSemana()).isNull();
        assertThat(request.intervaloDias()).isNull();
        assertThat(request.fechaInicio()).isNull();
        assertThat(request.fechaObjetivo()).isNull();
    }

    @Test
    void semanalConservaSoloElDiaYPersonalizadaSoloIntervaloEInicio() {
        GuardarMetaRequest semanal = new GuardarMetaRequest(TipoMeta.MONTO_MENSUAL, 1L,
                FrecuenciaMeta.SEMANAL, 3, 14, INICIO, OBJETIVO);
        GuardarMetaRequest personalizada = new GuardarMetaRequest(TipoMeta.MONTO_MENSUAL, 1L,
                FrecuenciaMeta.PERSONALIZADA, 3, 14, INICIO, OBJETIVO);

        assertThat(semanal.diaSemana()).isEqualTo(3);
        assertThat(semanal.intervaloDias()).isNull();
        assertThat(semanal.fechaInicio()).isNull();
        assertThat(personalizada.diaSemana()).isNull();
        assertThat(personalizada.intervaloDias()).isEqualTo(14);
        assertThat(personalizada.fechaInicio()).isEqualTo(INICIO);
        assertThat(personalizada.fechaObjetivo()).isNull();
    }

    private static GuardarMetaRequest semanal(Integer dia) {
        return new GuardarMetaRequest(
                TipoMeta.MONTO_MENSUAL, 1L, FrecuenciaMeta.SEMANAL, dia, null, null, null);
    }

    private static GuardarMetaRequest personalizada(Integer intervalo, LocalDate inicio) {
        return new GuardarMetaRequest(TipoMeta.MONTO_MENSUAL, 1L,
                FrecuenciaMeta.PERSONALIZADA, null, intervalo, inicio, null);
    }
}
