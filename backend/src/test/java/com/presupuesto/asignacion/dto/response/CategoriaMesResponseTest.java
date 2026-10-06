package com.presupuesto.asignacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import org.junit.jupiter.api.Test;

class CategoriaMesResponseTest {

    private static Categoria comida() {
        return Categoria.builder().id(7L).nombre("Comida").build();
    }

    @Test
    void mapeaTodosLosCampos() {
        CategoriaMesResponse respuesta =
                CategoriaMesResponse.desde(comida(), 100_000L, -30_000L, 70_000L);

        assertThat(respuesta).isEqualTo(
                new CategoriaMesResponse(7L, "Comida", false, 100_000L, -30_000L, 70_000L, false));
    }

    @Test
    void sobregastadaSoloConDisponibleNegativo() {
        assertThat(CategoriaMesResponse.desde(comida(), 0L, 0L, -1L).sobregastada()).isTrue();
        assertThat(CategoriaMesResponse.desde(comida(), 0L, 0L, 0L).sobregastada()).isFalse();
        assertThat(CategoriaMesResponse.desde(comida(), 0L, 0L, 1L).sobregastada()).isFalse();
    }

    @Test
    void reflejaLaCategoriaOculta() {
        Categoria oculta = comida();
        oculta.ocultar();

        assertThat(CategoriaMesResponse.desde(oculta, 0L, 0L, 0L).oculta()).isTrue();
    }
}
