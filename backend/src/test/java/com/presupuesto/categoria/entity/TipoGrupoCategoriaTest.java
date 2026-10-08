package com.presupuesto.categoria.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TipoGrupoCategoriaTest {

    @Test
    void tieneLosDosTiposDeGrupo() {
        assertThat(TipoGrupoCategoria.values())
                .containsExactly(TipoGrupoCategoria.NORMAL, TipoGrupoCategoria.PAGOS_TARJETA);
        assertThat(TipoGrupoCategoria.valueOf("PAGOS_TARJETA")).isNotNull();
    }
}
