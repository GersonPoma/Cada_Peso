package com.presupuesto.reporte.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngresosGastosResponseTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final YearMonth NOVIEMBRE = YearMonth.of(2026, 11);

    @Test
    void elNetoDeUnMesEsIngresosMenosGastos() {
        MesIngresosGastosResponse mes =
                MesIngresosGastosResponse.desde(OCTUBRE, 500_000L, 265_000L);

        assertThat(mes.mes()).isEqualTo("2026-10");
        assertThat(mes.neto()).isEqualTo(235_000L);
    }

    @Test
    void losTotalesSonLaSumaDeLosMeses() {
        IngresosGastosResponse respuesta = IngresosGastosResponse.desde(OCTUBRE, NOVIEMBRE, List.of(
                MesIngresosGastosResponse.desde(OCTUBRE, 500_000L, 265_000L),
                MesIngresosGastosResponse.desde(NOVIEMBRE, 0L, 0L)));

        assertThat(respuesta.desde()).isEqualTo("2026-10");
        assertThat(respuesta.hasta()).isEqualTo("2026-11");
        assertThat(respuesta.ingresos()).isEqualTo(500_000L);
        assertThat(respuesta.gastos()).isEqualTo(265_000L);
        assertThat(respuesta.neto()).isEqualTo(235_000L);
    }

    @Test
    void conGastosMayoresQueIngresosElNetoEsNegativo() {
        IngresosGastosResponse respuesta = IngresosGastosResponse.desde(OCTUBRE, OCTUBRE, List.of(
                MesIngresosGastosResponse.desde(OCTUBRE, 1_000L, 4_000L)));

        assertThat(respuesta.neto()).isEqualTo(-3_000L);
    }
}
