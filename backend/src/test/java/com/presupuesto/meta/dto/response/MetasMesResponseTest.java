package com.presupuesto.meta.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.meta.entity.TipoMeta;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetasMesResponseTest {

    private static MetaMesResponse meta(long id, long faltante, EstadoMeta estado) {
        return new MetaMesResponse(id, "Cat " + id, TipoMeta.MONTO_MENSUAL, 100_000L,
                100_000L, 0L, 0L, faltante, estado);
    }

    @Test
    void sumaElFaltanteYFormateaElMes() {
        List<MetaMesResponse> metas = List.of(
                meta(1L, 40_000L, EstadoMeta.FALTA),
                meta(2L, 10_000L, EstadoMeta.FALTA),
                meta(3L, 0L, EstadoMeta.POSPUESTA));

        MetasMesResponse respuesta = MetasMesResponse.desde(YearMonth.of(2026, 10), metas);

        assertThat(respuesta.mes()).isEqualTo("2026-10");
        assertThat(respuesta.totalFaltante()).isEqualTo(50_000L);
        assertThat(respuesta.metas()).isEqualTo(metas);
    }

    @Test
    void sinMetasElTotalEsCero() {
        MetasMesResponse respuesta = MetasMesResponse.desde(YearMonth.of(2026, 1), List.of());

        assertThat(respuesta.totalFaltante()).isZero();
        assertThat(respuesta.metas()).isEmpty();
    }
}
