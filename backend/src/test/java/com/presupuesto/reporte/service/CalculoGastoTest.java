package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.reporte.service.CalculoGasto.Gasto;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalculoGastoTest {

    private static final long COMIDA = 1L;
    private static final long HOGAR = 2L;
    private static final long METAS_DE_AHORRO = 3L;
    private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9);
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final YearMonth NOVIEMBRE = YearMonth.of(2026, 11);

    private static RangoMeses rango(YearMonth desde, YearMonth hasta) {
        return new RangoMeses(desde, hasta);
    }

    private static void poner(
            Map<Long, Map<YearMonth, Long>> actividad, long categoria, YearMonth mes, long valor) {
        actividad.computeIfAbsent(categoria, clave -> new HashMap<>()).merge(mes, valor, Long::sum);
    }

    @Test
    void elGastoEsElNegativoDeLaActividadComoEnElEjemploDeOctubre() {
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        poner(actividad, COMIDA, OCTUBRE, -80_000L);
        poner(actividad, COMIDA, OCTUBRE, -40_000L);
        poner(actividad, COMIDA, OCTUBRE, -30_000L);
        poner(actividad, COMIDA, OCTUBRE, 10_000L);
        poner(actividad, HOGAR, OCTUBRE, -20_000L);
        poner(actividad, METAS_DE_AHORRO, OCTUBRE, -100_000L);

        Gasto gasto = CalculoGasto.calcular(actividad, rango(OCTUBRE, OCTUBRE));

        assertThat(gasto.porCategoria()).containsEntry(COMIDA, 140_000L)
                .containsEntry(HOGAR, 20_000L)
                .containsEntry(METAS_DE_AHORRO, 100_000L);
        assertThat(gasto.porMes()).containsOnlyKeys(OCTUBRE).containsEntry(OCTUBRE, 260_000L);
    }

    @Test
    void sumaLosMesesDelRangoYDescartaLosAnterioresADesde() {
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        poner(actividad, COMIDA, YearMonth.of(2026, 8), -999_000L);
        poner(actividad, COMIDA, SEPTIEMBRE, -100_000L);
        poner(actividad, COMIDA, OCTUBRE, -50_000L);

        Gasto gasto = CalculoGasto.calcular(actividad, rango(SEPTIEMBRE, OCTUBRE));

        assertThat(gasto.porCategoria()).containsOnly(Map.entry(COMIDA, 150_000L));
        assertThat(gasto.porMes()).containsEntry(SEPTIEMBRE, 100_000L)
                .containsEntry(OCTUBRE, 50_000L);
    }

    @Test
    void unaCategoriaSoloConMesesAnterioresAlRangoNoAparece() {
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        poner(actividad, HOGAR, YearMonth.of(2026, 1), -5_000L);

        Gasto gasto = CalculoGasto.calcular(actividad, rango(SEPTIEMBRE, OCTUBRE));

        assertThat(gasto.porCategoria()).isEmpty();
    }

    @Test
    void unReembolsoMayorQueElGastoDejaElTotalNegativo() {
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        poner(actividad, COMIDA, OCTUBRE, -10_000L);
        poner(actividad, COMIDA, OCTUBRE, 30_000L);

        Gasto gasto = CalculoGasto.calcular(actividad, rango(OCTUBRE, OCTUBRE));

        assertThat(gasto.porCategoria()).containsEntry(COMIDA, -20_000L);
        assertThat(gasto.porMes()).containsEntry(OCTUBRE, -20_000L);
    }

    @Test
    void unaCategoriaConNetoCeroSigueApareciendo() {
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        poner(actividad, COMIDA, OCTUBRE, -10_000L);
        poner(actividad, COMIDA, OCTUBRE, 10_000L);

        Gasto gasto = CalculoGasto.calcular(actividad, rango(OCTUBRE, OCTUBRE));

        assertThat(gasto.porCategoria()).containsOnlyKeys(COMIDA).containsEntry(COMIDA, 0L);
    }

    @Test
    void unMesSinDatosVieneEnCeroYElRangoSinActividadEstaVacio() {
        Gasto gasto = CalculoGasto.calcular(Map.of(), rango(SEPTIEMBRE, NOVIEMBRE));

        assertThat(gasto.porCategoria()).isEmpty();
        assertThat(gasto.porMes()).containsOnlyKeys(SEPTIEMBRE, OCTUBRE, NOVIEMBRE)
                .containsValues(0L, 0L, 0L);
        assertThat(CalculoGasto.total(gasto.porMes())).isZero();
    }

    @Test
    void sinCategoriaSumaLasSalidasSimplesYDivididasConSignoCambiado() {
        Map<YearMonth, Long> simples = Map.of(OCTUBRE, -5_000L, NOVIEMBRE, -1_000L);
        Map<YearMonth, Long> divididas = Map.of(OCTUBRE, -10_000L, YearMonth.of(2026, 1), -7L);

        Map<YearMonth, Long> porMes =
                CalculoGasto.sinCategoria(simples, divididas, rango(OCTUBRE, NOVIEMBRE));

        assertThat(porMes).containsEntry(OCTUBRE, 15_000L).containsEntry(NOVIEMBRE, 1_000L);
        assertThat(CalculoGasto.total(porMes)).isEqualTo(16_000L);
    }
}
