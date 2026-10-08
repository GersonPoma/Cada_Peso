package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalculoPagoTarjetaTest {

    private static final long VISA = 10L;
    private static final long MASTER = 11L;
    private static final long PAGO_VISA = 100L;
    private static final long PAGO_MASTER = 101L;
    private static final YearMonth ENERO = YearMonth.of(2026, 1);
    private static final YearMonth FEBRERO = YearMonth.of(2026, 2);
    private static final Map<Long, Long> CATEGORIAS = Map.of(VISA, PAGO_VISA, MASTER, PAGO_MASTER);

    @Test
    void unGastoSumaSuMontoCompletoALaCategoriaDePago() {
        Map<Long, Map<YearMonth, Long>> sumas = datos(VISA, ENERO, -30_000L);

        assertThat(CalculoPagoTarjeta.actividadPorCategoria(sumas, CATEGORIAS))
                .containsOnlyKeys(PAGO_VISA)
                .containsEntry(PAGO_VISA, Map.of(ENERO, 30_000L));
    }

    @Test
    void unReembolsoRestaDeLaReserva() {
        Map<Long, Map<YearMonth, Long>> sumas = datos(VISA, ENERO, -30_000L + 5_000L);

        assertThat(CalculoPagoTarjeta.actividadPorCategoria(sumas, CATEGORIAS).get(PAGO_VISA))
                .containsEntry(ENERO, 25_000L);
    }

    @Test
    void unPagoEntraPositivoALaTarjetaYRestaDeLaReserva() {
        Map<Long, Map<YearMonth, Long>> sumas = datos(VISA, ENERO, 30_000L);

        assertThat(CalculoPagoTarjeta.actividadPorCategoria(sumas, CATEGORIAS).get(PAGO_VISA))
                .containsEntry(ENERO, -30_000L);
    }

    @Test
    void variasTarjetasYMesesVanACadaUnaDeSusCategorias() {
        Map<Long, Map<YearMonth, Long>> sumas = datos(VISA, ENERO, -10_000L);
        agregar(sumas, VISA, FEBRERO, -20_000L);
        agregar(sumas, MASTER, ENERO, -7_000L);

        Map<Long, Map<YearMonth, Long>> actividad =
                CalculoPagoTarjeta.actividadPorCategoria(sumas, CATEGORIAS);

        assertThat(actividad.get(PAGO_VISA))
                .containsEntry(ENERO, 10_000L)
                .containsEntry(FEBRERO, 20_000L);
        assertThat(actividad.get(PAGO_MASTER)).containsExactly(Map.entry(ENERO, 7_000L));
    }

    @Test
    void unaTarjetaSinCategoriaDePagoSeIgnora() {
        Map<Long, Map<YearMonth, Long>> sumas = datos(99L, ENERO, -30_000L);

        assertThat(CalculoPagoTarjeta.actividadPorCategoria(sumas, CATEGORIAS)).isEmpty();
    }

    @Test
    void sinDatosNoHayActividad() {
        assertThat(CalculoPagoTarjeta.actividadPorCategoria(Map.of(), CATEGORIAS)).isEmpty();
        assertThat(CalculoPagoTarjeta.actividadPorCategoria(datos(VISA, ENERO, 1L), Map.of()))
                .isEmpty();
    }

    private static Map<Long, Map<YearMonth, Long>> datos(long id, YearMonth mes, long valor) {
        Map<Long, Map<YearMonth, Long>> datos = new HashMap<>();
        agregar(datos, id, mes, valor);
        return datos;
    }

    private static void agregar(
            Map<Long, Map<YearMonth, Long>> datos, long id, YearMonth mes, long valor) {
        datos.computeIfAbsent(id, clave -> new HashMap<>()).put(mes, valor);
    }
}
