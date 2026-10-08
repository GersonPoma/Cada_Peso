package com.presupuesto.categoria.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GrupoCategoriaResponseTest {

    @Test
    void desdeCopiaTodosLosCamposDeLaEntidad() {
        Instant creada = Instant.parse("2026-01-01T10:00:00Z");
        Instant actualizada = Instant.parse("2026-01-02T10:00:00Z");
        GrupoCategoria grupo = GrupoCategoria.builder()
                .id(5L)
                .nombre("Vivienda")
                .orden(2)
                .fechaCreacion(creada)
                .fechaActualizacion(actualizada)
                .build();
        grupo.ocultar();

        GrupoCategoriaResponse response = GrupoCategoriaResponse.desde(grupo);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.nombre()).isEqualTo("Vivienda");
        assertThat(response.orden()).isEqualTo(2);
        assertThat(response.oculto()).isTrue();
        assertThat(response.fechaCreacion()).isEqualTo(creada);
        assertThat(response.fechaActualizacion()).isEqualTo(actualizada);
    }

    @Test
    void desdeIncluyeElTipoDelGrupo() {
        GrupoCategoria normal = GrupoCategoria.builder().id(1L).nombre("Vivienda").build();
        GrupoCategoria pagos = GrupoCategoria.builder()
                .id(2L).nombre("Pagos").tipo(TipoGrupoCategoria.PAGOS_TARJETA).build();

        assertThat(GrupoCategoriaResponse.desde(normal).tipo())
                .isEqualTo(TipoGrupoCategoria.NORMAL);
        assertThat(GrupoCategoriaResponse.desde(pagos).tipo())
                .isEqualTo(TipoGrupoCategoria.PAGOS_TARJETA);
    }
}
