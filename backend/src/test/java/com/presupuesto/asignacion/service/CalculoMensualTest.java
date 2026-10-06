package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalculoMensualTest {

    private static final long COMIDA = 1L;
    private static final long OCIO = 2L;
    private static final YearMonth ENERO = YearMonth.of(2026, 1);
    private static final YearMonth FEBRERO = YearMonth.of(2026, 2);
    private static final YearMonth MARZO = YearMonth.of(2026, 3);

    @Test
    void elSaldoPositivoPasaAlMesSiguiente() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 100_000L);
        agregar(asignado, COMIDA, FEBRERO, 50_000L);
        Map<Long, Map<YearMonth, Long>> actividad = datos(COMIDA, ENERO, -30_000L);
        agregar(actividad, COMIDA, FEBRERO, -20_000L);

        ResultadoMes enero = CalculoMensual.calcular(ENERO, asignado, actividad, 0L);
        ResultadoMes febrero = CalculoMensual.calcular(FEBRERO, asignado, actividad, 0L);

        assertThat(enero.fila(COMIDA)).isEqualTo(new FilaMes(100_000L, -30_000L, 70_000L));
        assertThat(febrero.fila(COMIDA)).isEqualTo(new FilaMes(50_000L, -20_000L, 100_000L));
    }

    @Test
    void elSobregastoNoSeArrastraALaCategoriaYBajaElListoDeLosMesesSiguientes() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 20_000L);
        agregar(asignado, COMIDA, FEBRERO, 10_000L);
        Map<Long, Map<YearMonth, Long>> actividad = datos(COMIDA, ENERO, -50_000L);
        long ingresos = 500_000L;

        ResultadoMes enero = CalculoMensual.calcular(ENERO, asignado, actividad, ingresos);
        ResultadoMes febrero = CalculoMensual.calcular(FEBRERO, asignado, actividad, ingresos);
        ResultadoMes marzo = CalculoMensual.calcular(MARZO, asignado, actividad, ingresos);

        assertThat(enero.fila(COMIDA).disponible()).isEqualTo(-30_000L);
        assertThat(febrero.fila(COMIDA).disponible()).isEqualTo(10_000L);
        assertThat(enero.listoParaAsignar()).isEqualTo(480_000L);
        assertThat(febrero.listoParaAsignar()).isEqualTo(440_000L);
        assertThat(marzo.listoParaAsignar()).isEqualTo(440_000L);
        assertThat(marzo.fila(COMIDA).disponible()).isEqualTo(10_000L);
    }

    @Test
    void sinDatosElListoSonLosIngresosYNoHayFilas() {
        ResultadoMes resultado = CalculoMensual.calcular(ENERO, Map.of(), Map.of(), 300_000L);

        assertThat(resultado.listoParaAsignar()).isEqualTo(300_000L);
        assertThat(resultado.filas()).isEmpty();
        assertThat(resultado.fila(COMIDA)).isEqualTo(FilaMes.CERO);
    }

    @Test
    void losMesesIntermediosSinDatosConservanElDisponiblePositivo() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 40_000L);

        ResultadoMes marzo = CalculoMensual.calcular(MARZO, asignado, Map.of(), 100_000L);

        assertThat(marzo.fila(COMIDA)).isEqualTo(new FilaMes(0L, 0L, 40_000L));
        assertThat(marzo.listoParaAsignar()).isEqualTo(60_000L);
    }

    @Test
    void antesDelPrimerMesConDatosTodoEsCero() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, MARZO, 40_000L);

        ResultadoMes enero = CalculoMensual.calcular(ENERO, asignado, Map.of(), 10_000L);

        assertThat(enero.filas()).isEmpty();
        assertThat(enero.listoParaAsignar()).isEqualTo(10_000L);
    }

    @Test
    void laActividadSinAsignacionDejaLaCategoriaSobregastada() {
        Map<Long, Map<YearMonth, Long>> actividad = datos(OCIO, ENERO, -9_000L);

        ResultadoMes enero = CalculoMensual.calcular(ENERO, Map.of(), actividad, 0L);

        assertThat(enero.fila(OCIO)).isEqualTo(new FilaMes(0L, -9_000L, -9_000L));
        assertThat(enero.listoParaAsignar()).isZero();
    }

    @Test
    void elListoPuedeSerNegativo() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 100_000L);
        agregar(asignado, OCIO, ENERO, 50_000L);

        ResultadoMes enero = CalculoMensual.calcular(ENERO, asignado, Map.of(), 100_000L);

        assertThat(enero.listoParaAsignar()).isEqualTo(-50_000L);
    }

    @Test
    void lasCategoriasSeCalculanPorSeparado() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 100_000L);
        agregar(asignado, OCIO, ENERO, 20_000L);
        Map<Long, Map<YearMonth, Long>> actividad = datos(COMIDA, ENERO, -30_000L);

        ResultadoMes enero = CalculoMensual.calcular(ENERO, asignado, actividad, 500_000L);

        assertThat(enero.fila(COMIDA).disponible()).isEqualTo(70_000L);
        assertThat(enero.fila(OCIO).disponible()).isEqualTo(20_000L);
        assertThat(enero.listoParaAsignar()).isEqualTo(380_000L);
    }

    @Test
    void unaAsignacionNegativaRestaDelDisponibleYSumaAlListo() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 100_000L);
        agregar(asignado, COMIDA, FEBRERO, -30_000L);

        ResultadoMes febrero = CalculoMensual.calcular(FEBRERO, asignado, Map.of(), 200_000L);

        assertThat(febrero.fila(COMIDA)).isEqualTo(new FilaMes(-30_000L, 0L, 70_000L));
        assertThat(febrero.listoParaAsignar()).isEqualTo(130_000L);
    }

    @Test
    void ignoraLosDatosPosterioresAlMesPedido() {
        Map<Long, Map<YearMonth, Long>> asignado = datos(COMIDA, ENERO, 10_000L);
        agregar(asignado, COMIDA, MARZO, 99_000L);

        ResultadoMes enero = CalculoMensual.calcular(ENERO, asignado, Map.of(), 50_000L);

        assertThat(enero.fila(COMIDA).asignado()).isEqualTo(10_000L);
        assertThat(enero.listoParaAsignar()).isEqualTo(40_000L);
    }

    private static Map<Long, Map<YearMonth, Long>> datos(
            long categoria, YearMonth mes, long valor) {
        Map<Long, Map<YearMonth, Long>> mapa = new HashMap<>();
        agregar(mapa, categoria, mes, valor);
        return mapa;
    }

    private static void agregar(
            Map<Long, Map<YearMonth, Long>> mapa, long categoria, YearMonth mes, long valor) {
        mapa.computeIfAbsent(categoria, clave -> new HashMap<>()).put(mes, valor);
    }
}
