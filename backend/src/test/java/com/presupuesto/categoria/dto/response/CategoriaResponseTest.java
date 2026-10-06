package com.presupuesto.categoria.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CategoriaResponseTest {

    @Test
    void desdeCopiaTodosLosCamposDeLaEntidad() {
        Instant creada = Instant.parse("2026-01-01T10:00:00Z");
        Instant actualizada = Instant.parse("2026-01-02T10:00:00Z");
        Categoria categoria = Categoria.builder()
                .id(8L)
                .grupo(GrupoCategoria.builder().id(3L).build())
                .nombre("Alquiler")
                .orden(1)
                .nota("Mensual")
                .fechaCreacion(creada)
                .fechaActualizacion(actualizada)
                .build();
        categoria.ocultar();

        CategoriaResponse response = CategoriaResponse.desde(categoria);

        assertThat(response.id()).isEqualTo(8L);
        assertThat(response.grupoId()).isEqualTo(3L);
        assertThat(response.nombre()).isEqualTo("Alquiler");
        assertThat(response.orden()).isEqualTo(1);
        assertThat(response.oculta()).isTrue();
        assertThat(response.nota()).isEqualTo("Mensual");
        assertThat(response.fechaCreacion()).isEqualTo(creada);
        assertThat(response.fechaActualizacion()).isEqualTo(actualizada);
    }
}
