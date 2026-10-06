package com.presupuesto.asignacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.GrupoCategoria;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrupoMesResponseTest {

    @Test
    void mapeaElGrupoYConservaElOrdenDeSusCategorias() {
        GrupoCategoria grupo = GrupoCategoria.builder().id(3L).nombre("Hogar").orden(2).build();
        List<CategoriaMesResponse> categorias = List.of(
                new CategoriaMesResponse(1L, "A", false, 1L, 0L, 1L, false),
                new CategoriaMesResponse(2L, "B", false, 2L, 0L, 2L, false));

        GrupoMesResponse respuesta = GrupoMesResponse.desde(grupo, categorias);

        assertThat(respuesta.id()).isEqualTo(3L);
        assertThat(respuesta.nombre()).isEqualTo("Hogar");
        assertThat(respuesta.orden()).isEqualTo(2);
        assertThat(respuesta.oculto()).isFalse();
        assertThat(respuesta.categorias()).containsExactlyElementsOf(categorias);
    }

    @Test
    void reflejaElGrupoOculto() {
        GrupoCategoria grupo = GrupoCategoria.builder().id(3L).nombre("Viejo").build();
        grupo.ocultar();

        assertThat(GrupoMesResponse.desde(grupo, List.of()).oculto()).isTrue();
    }
}
