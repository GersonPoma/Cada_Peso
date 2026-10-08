package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.reporte.service.CalculoCumplimiento.Mes;
import com.presupuesto.reporte.service.CalculoCumplimiento.Total;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculoCumplimientoTest {

    @Test
    void elPorcentajeDeUnMesEsAsignadoSobreNecesidad() {
        // meta mensual de 100000 con 80000 asignados: 80,00 %
        assertThat(CalculoCumplimiento.porcentaje(80_000L, 100_000L)).isEqualTo(8_000L);
        assertThat(CalculoCumplimiento.porcentaje(100_000L, 100_000L)).isEqualTo(10_000L);
        assertThat(CalculoCumplimiento.porcentaje(0L, 100_000L)).isZero();
    }

    @Test
    void asignarDeMasSuperaElCienPorCientoSinTope() {
        assertThat(CalculoCumplimiento.porcentaje(150_000L, 100_000L)).isEqualTo(15_000L);
    }

    @Test
    void sinNecesidadElPorcentajeEsNulo() {
        assertThat(CalculoCumplimiento.porcentaje(0L, 0L)).isNull();
        assertThat(CalculoCumplimiento.porcentaje(50_000L, 0L)).isNull();
        assertThat(CalculoCumplimiento.porcentaje(50_000L, -1L)).isNull();
    }

    @Test
    void elTotalDeDosMesesUsaLasSumasDelRango() {
        Total total = CalculoCumplimiento.totales(List.of(
                new Mes(100_000L, 100_000L, 60_000L), new Mes(100_000L, 80_000L, 0L)));

        assertThat(total.necesidad()).isEqualTo(200_000L);
        assertThat(total.asignado()).isEqualTo(180_000L);
        assertThat(total.gastado()).isEqualTo(60_000L);
        assertThat(total.porcentaje()).isEqualTo(9_000L);
    }

    @Test
    void elTotalNoEsElPromedioDeLosPorcentajesMensuales() {
        // un mes de 100000 al 100 % y otro de 300000 al 0 %: 25 %, no 50 %
        Total total = CalculoCumplimiento.totales(List.of(
                new Mes(100_000L, 100_000L, 0L), new Mes(300_000L, 0L, 0L)));

        assertThat(total.porcentaje()).isEqualTo(2_500L);
    }

    @Test
    void unaMetaPospuestaTodoElRangoTieneTotalSinPorcentaje() {
        Total total = CalculoCumplimiento.totales(List.of(
                new Mes(0L, 0L, 3_000L), new Mes(0L, 5_000L, 0L)));

        assertThat(total.necesidad()).isZero();
        assertThat(total.asignado()).isEqualTo(5_000L);
        assertThat(total.porcentaje()).isNull();
    }

    @Test
    void sinMesesTodoVale0YElPorcentajeEsNulo() {
        Total total = CalculoCumplimiento.totales(List.of());

        assertThat(total).isEqualTo(new Total(0L, 0L, 0L, null));
    }

    @Test
    void elGastadoEsElNegativoDeLaActividad() {
        assertThat(CalculoCumplimiento.gastado(-60_000L, false)).isEqualTo(60_000L);
        assertThat(CalculoCumplimiento.gastado(10_000L, false)).isEqualTo(-10_000L);
        assertThat(CalculoCumplimiento.gastado(0L, false)).isZero();
    }

    @Test
    void enUnaCategoriaDePagoDeTarjetaLoGastadoEsCeroAunqueHayaReserva() {
        assertThat(CalculoCumplimiento.gastado(30_000L, true)).isZero();
        assertThat(CalculoCumplimiento.gastado(-30_000L, true)).isZero();
    }
}
