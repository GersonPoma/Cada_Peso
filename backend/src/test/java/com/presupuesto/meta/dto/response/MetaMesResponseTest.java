package com.presupuesto.meta.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import org.junit.jupiter.api.Test;

class MetaMesResponseTest {

    @Test
    void combinaLaMetaConLasCifrasDelMes() {
        Meta meta = Meta.builder()
                .categoria(Categoria.builder().id(3L).nombre("Comida").build())
                .tipo(TipoMeta.SALDO_OBJETIVO)
                .monto(300_000L)
                .build();

        MetaMesResponse respuesta = MetaMesResponse.desde(
                meta, 180_000L, 0L, 120_000L, 180_000L, EstadoMeta.FALTA);

        assertThat(respuesta).isEqualTo(new MetaMesResponse(3L, "Comida", TipoMeta.SALDO_OBJETIVO,
                300_000L, 180_000L, 0L, 120_000L, 180_000L, EstadoMeta.FALTA));
    }
}
