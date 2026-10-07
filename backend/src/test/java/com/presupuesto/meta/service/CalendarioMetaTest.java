package com.presupuesto.meta.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CalendarioMetaTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 2);

    // ---------- semanales (ejemplo B) ----------

    @Test
    void octubreDe2026TieneCuatroLunesYNoviembreCinco() {
        assertThat(CalendarioMeta.vencimientosSemanales(YearMonth.of(2026, 10), 1)).isEqualTo(4);
        assertThat(CalendarioMeta.vencimientosSemanales(YearMonth.of(2026, 11), 1)).isEqualTo(5);
    }

    @Test
    void cadaDiaDeLaSemanaSeCuentaConSuNumero() {
        // Octubre de 2026 empieza en jueves y tiene 31 días: jueves, viernes y sábado, 5 veces.
        YearMonth octubre = YearMonth.of(2026, 10);
        assertThat(CalendarioMeta.vencimientosSemanales(octubre, 4)).isEqualTo(5);
        assertThat(CalendarioMeta.vencimientosSemanales(octubre, 5)).isEqualTo(5);
        assertThat(CalendarioMeta.vencimientosSemanales(octubre, 6)).isEqualTo(5);
        assertThat(CalendarioMeta.vencimientosSemanales(octubre, 7)).isEqualTo(4);
    }

    @Test
    void febreroDeUnAnioNoBisiestoTieneCuatroDeCadaDia() {
        for (int dia = 1; dia <= 7; dia++) {
            assertThat(CalendarioMeta.vencimientosSemanales(YearMonth.of(2027, 2), dia))
                    .isEqualTo(4);
        }
    }

    // ---------- personalizadas (ejemplo C y C2) ----------

    @Test
    void cadaCatorceDiasDesdeElDosDeOctubre() {
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2026, 10), INICIO, 14))
                .isEqualTo(3); // 2, 16, 30
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2026, 11), INICIO, 14))
                .isEqualTo(2); // 13, 27
    }

    @Test
    void antesDeLaFechaDeInicioNoHayVencimientos() {
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2026, 9), INICIO, 14))
                .isZero();
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2020, 1), INICIO, 2))
                .isZero();
    }

    @Test
    void cadaTreintaDiasFebreroDe2027NoTieneVencimientosPorqueElSiguienteEsElUnoDeMarzo() {
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2027, 2), INICIO, 30))
                .isZero();
    }

    @Test
    void cadaTreintaDiasDiciembreDe2026TieneElUnoYElTreintaYUno() {
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2026, 12), INICIO, 30))
                .isEqualTo(2);
    }

    @Test
    void cadaTreintaDiasLosDemasMesesDelEjemplo() {
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2026, 10), INICIO, 30))
                .isEqualTo(1); // 2
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2026, 11), INICIO, 30))
                .isEqualTo(1); // 1
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2027, 1), INICIO, 30))
                .isEqualTo(1); // 30
        assertThat(CalendarioMeta.vencimientosPersonalizados(YearMonth.of(2027, 3), INICIO, 30))
                .isEqualTo(2); // 1, 31
    }

    @Test
    void unVencimientoEnElPrimerOUltimoDiaDelMesCuenta() {
        assertThat(CalendarioMeta.vencimientosPersonalizados(
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 1), 365)).isEqualTo(1);
        assertThat(CalendarioMeta.vencimientosPersonalizados(
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 31), 365)).isEqualTo(1);
        assertThat(CalendarioMeta.vencimientosPersonalizados(
                YearMonth.of(2026, 10), LocalDate.of(2026, 11, 1), 2)).isZero();
    }

    @Test
    void elIntervaloCruzaElCambioDeAnio() {
        // 2026-12-31 + 7k: 2026-12-31, 2027-01-07, 14, 21, 28
        assertThat(CalendarioMeta.vencimientosPersonalizados(
                YearMonth.of(2027, 1), LocalDate.of(2026, 12, 31), 7)).isEqualTo(4);
    }

    // ---------- meses restantes (ejemplo D) ----------

    @Test
    void mesesRestantesIncluyenAmbosExtremos() {
        LocalDate objetivo = LocalDate.of(2026, 12, 15);
        assertThat(CalendarioMeta.mesesRestantes(YearMonth.of(2026, 10), objetivo)).isEqualTo(3);
        assertThat(CalendarioMeta.mesesRestantes(YearMonth.of(2026, 12), objetivo)).isEqualTo(1);
        assertThat(CalendarioMeta.mesesRestantes(YearMonth.of(2025, 12), objetivo)).isEqualTo(13);
    }

    @Test
    void conUnObjetivoPasadoQuedaUnSoloMes() {
        LocalDate objetivo = LocalDate.of(2026, 12, 15);
        assertThat(CalendarioMeta.mesesRestantes(YearMonth.of(2027, 1), objetivo)).isEqualTo(1);
        assertThat(CalendarioMeta.mesesRestantes(YearMonth.of(2030, 6), objetivo)).isEqualTo(1);
    }

    // ---------- redondeo hacia arriba ----------

    @Test
    void laDivisionSeRedondeaHaciaArribaAlMilliunit() {
        assertThat(CalendarioMeta.divisionHaciaArriba(600_000L, 3)).isEqualTo(200_000L);
        assertThat(CalendarioMeta.divisionHaciaArriba(100_000L, 3)).isEqualTo(33_334L);
        assertThat(CalendarioMeta.divisionHaciaArriba(1L, 3)).isEqualTo(1L);
        assertThat(CalendarioMeta.divisionHaciaArriba(7L, 1)).isEqualTo(7L);
    }
}
