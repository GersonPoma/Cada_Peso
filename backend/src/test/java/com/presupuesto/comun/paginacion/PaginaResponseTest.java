package com.presupuesto.comun.paginacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PaginaResponseTest {

    @Test
    void mapeaElContenidoYCopiaNumeroTamanoYTotales() {
        PageImpl<Integer> pagina = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 8);

        PaginaResponse<String> respuesta = PaginaResponse.desde(pagina, n -> "n" + n);

        assertThat(respuesta.contenido()).containsExactly("n1", "n2", "n3");
        assertThat(respuesta.pagina()).isEqualTo(1);
        assertThat(respuesta.tamano()).isEqualTo(3);
        assertThat(respuesta.totalElementos()).isEqualTo(8);
        assertThat(respuesta.totalPaginas()).isEqualTo(3);
    }

    @Test
    void unaPaginaVaciaTieneCeroPaginas() {
        PageImpl<Integer> vacia = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PaginaResponse<Integer> respuesta = PaginaResponse.desde(vacia, n -> n);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.totalElementos()).isZero();
        assertThat(respuesta.totalPaginas()).isZero();
    }
}
