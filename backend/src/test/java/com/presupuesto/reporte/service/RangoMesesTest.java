package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RangoMesesTest {

    private static final int MAXIMO = 60;

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "ayer", "1999-12", "2101-01", "2026-00", " "})
    void unDesdeInvalidoEsDatosInvalidos(String desde) {
        assertThatThrownBy(() -> RangoMeses.interpretar(desde, "2026-10", MAXIMO, false))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "ayer", "1999-12", "2101-01"})
    void unHastaInvalidoEsDatosInvalidos(String hasta) {
        assertThatThrownBy(() -> RangoMeses.interpretar("2026-10", hasta, MAXIMO, true))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void sinDesdeEsDatosInvalidos(String desde) {
        assertThatThrownBy(() -> RangoMeses.interpretar(desde, "2026-10", MAXIMO, true))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void sinHastaEsDatosInvalidosSiEsObligatorio(String hasta) {
        assertThatThrownBy(() -> RangoMeses.interpretar("2026-10", hasta, MAXIMO, false))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void sinHastaEsElMismoMesSiEsOpcional(String hasta) {
        RangoMeses rango = RangoMeses.interpretar("2026-10", hasta, MAXIMO, true);

        assertThat(rango.desde()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(rango.hasta()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void unRangoInvertidoEsDatosInvalidos() {
        assertThatThrownBy(() -> RangoMeses.interpretar("2026-10", "2026-09", MAXIMO, false))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void sesentaMesesPasanYSesentaYUnoNo() {
        RangoMeses sesenta = RangoMeses.interpretar("2022-01", "2026-12", 60, false);

        assertThat(sesenta.meses()).hasSize(60);
        assertThatThrownBy(() -> RangoMeses.interpretar("2022-01", "2027-01", 60, false))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void respetaUnMaximoConfiguradoEnDoce() {
        assertThat(RangoMeses.interpretar("2026-01", "2026-12", 12, false).meses()).hasSize(12);
        assertThatThrownBy(() -> RangoMeses.interpretar("2026-01", "2027-01", 12, false))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void unSoloMesTieneUnElemento() {
        RangoMeses rango = RangoMeses.interpretar("2026-10", "2026-10", MAXIMO, false);

        assertThat(rango.meses()).containsExactly(YearMonth.of(2026, 10));
    }

    @Test
    void losMesesVanEnOrdenYCruzanElAnio() {
        RangoMeses rango = RangoMeses.interpretar("2025-11", "2026-02", MAXIMO, false);

        assertThat(rango.meses()).containsExactly(
                YearMonth.of(2025, 11), YearMonth.of(2025, 12),
                YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    void ningunMensajeReflejaElValorRecibido() {
        String valor = "valor-raro-xyz";

        assertThatThrownBy(() -> RangoMeses.interpretar(valor, "2026-10", MAXIMO, false))
                .hasMessageNotContaining(valor);
        assertThatThrownBy(() -> RangoMeses.interpretar("2026-10", valor, MAXIMO, false))
                .hasMessageNotContaining(valor);
    }
}
