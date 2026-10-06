package com.presupuesto.categoria.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrupoCategoriaConCategoriasResponseTest {

    @Test
    void desdeCopiaElGrupoYConservaElOrdenDeLasCategorias() {
        GrupoCategoria grupo =
                GrupoCategoria.builder().id(3L).nombre("Vivienda").orden(1).build();
        Categoria luz = Categoria.builder().id(1L).grupo(grupo).nombre("Luz").build();
        Categoria agua = Categoria.builder().id(2L).grupo(grupo).nombre("Agua").build();

        GrupoCategoriaConCategoriasResponse response =
                GrupoCategoriaConCategoriasResponse.desde(grupo, List.of(luz, agua));

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.nombre()).isEqualTo("Vivienda");
        assertThat(response.orden()).isEqualTo(1);
        assertThat(response.oculto()).isFalse();
        assertThat(response.categorias()).extracting(CategoriaResponse::nombre)
                .containsExactly("Luz", "Agua");
    }

    @Test
    void sinCategoriasLaListaVaVacia() {
        GrupoCategoria grupo = GrupoCategoria.builder().id(3L).nombre("Vacio").build();

        assertThat(GrupoCategoriaConCategoriasResponse.desde(grupo, List.of()).categorias())
                .isEmpty();
    }
}
