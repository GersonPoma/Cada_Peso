package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PorcentajeTest {

    private static final long TOTAL_OCTUBRE = 265_000L;

    @Test
    void calculaLosPorcentajesDelEjemploDeOctubre() {
        assertThat(Porcentaje.centesimas(140_000L, TOTAL_OCTUBRE)).isEqualTo(5283L);
        assertThat(Porcentaje.centesimas(100_000L, TOTAL_OCTUBRE)).isEqualTo(3774L);
        assertThat(Porcentaje.centesimas(20_000L, TOTAL_OCTUBRE)).isEqualTo(755L);
        assertThat(Porcentaje.centesimas(5_000L, TOTAL_OCTUBRE)).isEqualTo(189L);
    }

    @Test
    void elMedioExactoSeAlejaDeCero() {
        assertThat(Porcentaje.centesimas(3L, 20_000L)).isEqualTo(2L);
        assertThat(Porcentaje.centesimas(-3L, 20_000L)).isEqualTo(-2L);
    }

    @Test
    void unaParteNegativaDaUnPorcentajeNegativo() {
        assertThat(Porcentaje.centesimas(-20_000L, TOTAL_OCTUBRE)).isEqualTo(-755L);
    }

    @Test
    void conTotalCeroONegativoDaCero() {
        assertThat(Porcentaje.centesimas(5_000L, 0L)).isZero();
        assertThat(Porcentaje.centesimas(5_000L, -1L)).isZero();
        assertThat(Porcentaje.centesimas(0L, 0L)).isZero();
    }

    @Test
    void unaParteMayorQueElTotalSuperaElCienPorCiento() {
        assertThat(Porcentaje.centesimas(150_000L, 100_000L)).isEqualTo(15_000L);
    }

    @Test
    void conMontosGrandesNoDesborda() {
        assertThat(Porcentaje.centesimas(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(10_000L);
        assertThat(Porcentaje.centesimas(Long.MAX_VALUE / 2, Long.MAX_VALUE)).isEqualTo(5_000L);
    }
}
