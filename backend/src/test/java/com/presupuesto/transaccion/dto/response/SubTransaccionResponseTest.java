package com.presupuesto.transaccion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.transaccion.entity.SubTransaccion;
import org.junit.jupiter.api.Test;

class SubTransaccionResponseTest {

    @Test
    void mapeaTodosLosCampos() {
        SubTransaccion sub = SubTransaccion.builder()
                .id(5L).monto(-700L).memo("pan").categoria(Categoria.builder().id(2L).build())
                .build();

        SubTransaccionResponse respuesta = SubTransaccionResponse.desde(sub);

        assertThat(respuesta).isEqualTo(new SubTransaccionResponse(5L, 2L, -700L, "pan"));
    }

    @Test
    void sinCategoriaNiMemoQuedanNulos() {
        SubTransaccionResponse respuesta =
                SubTransaccionResponse.desde(SubTransaccion.builder().id(6L).monto(1L).build());

        assertThat(respuesta.categoriaId()).isNull();
        assertThat(respuesta.memo()).isNull();
    }
}
