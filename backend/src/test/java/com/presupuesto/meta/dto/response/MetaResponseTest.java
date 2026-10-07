package com.presupuesto.meta.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MetaResponseTest {

    @Test
    void copiaTodosLosCamposDeLaMeta() {
        Meta meta = Meta.builder().categoria(Categoria.builder().id(7L).build()).build();
        meta.reemplazar(TipoMeta.MONTO_MENSUAL, 10_000L, FrecuenciaMeta.PERSONALIZADA, null, 14,
                LocalDate.of(2026, 10, 2), null);

        MetaResponse respuesta = MetaResponse.desde(meta);

        assertThat(respuesta).isEqualTo(new MetaResponse(7L, TipoMeta.MONTO_MENSUAL, 10_000L,
                FrecuenciaMeta.PERSONALIZADA, null, 14, LocalDate.of(2026, 10, 2), null));
    }

    @Test
    void losCamposQueNoAplicanVanEnNull() {
        Meta meta = Meta.builder().categoria(Categoria.builder().id(8L).build()).build();
        meta.reemplazar(TipoMeta.SALDO_OBJETIVO, 300_000L, null, null, null, null, null);

        MetaResponse respuesta = MetaResponse.desde(meta);

        assertThat(respuesta.categoriaId()).isEqualTo(8L);
        assertThat(respuesta.frecuencia()).isNull();
        assertThat(respuesta.fechaObjetivo()).isNull();
    }
}
