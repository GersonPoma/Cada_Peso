package com.presupuesto.meta.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutoAsignarResponseTest {

    @Test
    void elCambioCopiaLaCategoriaYLosDosAsignados() {
        Categoria comida = Categoria.builder().id(1L).nombre("Comida").build();

        assertThat(CambioAsignacionResponse.desde(comida, 30_000L, 100_000L))
                .isEqualTo(new CambioAsignacionResponse(1L, "Comida", 30_000L, 100_000L));
    }

    @Test
    void listoParaAsignarDespuesEsElDeAntesMenosLaSumaDeLasDiferencias() {
        List<CambioAsignacionResponse> cambios = List.of(
                new CambioAsignacionResponse(1L, "Comida", 30_000L, 100_000L),
                new CambioAsignacionResponse(2L, "Ocio", 50_000L, 20_000L));

        AutoAsignarResponse respuesta = AutoAsignarResponse.desde(true, 200_000L, cambios);

        assertThat(respuesta.aplicado()).isTrue();
        assertThat(respuesta.listoParaAsignarAntes()).isEqualTo(200_000L);
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(160_000L);
        assertThat(respuesta.cambios()).isEqualTo(cambios);
    }

    @Test
    void listoParaAsignarDespuesPuedeQuedarNegativo() {
        AutoAsignarResponse respuesta = AutoAsignarResponse.desde(false, 10_000L, List.of(
                new CambioAsignacionResponse(1L, "Comida", 0L, 50_000L)));

        assertThat(respuesta.aplicado()).isFalse();
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(-40_000L);
    }

    @Test
    void sinCambiosLosDosListoSonIguales() {
        AutoAsignarResponse respuesta = AutoAsignarResponse.desde(true, 77_000L, List.of());

        assertThat(respuesta.cambios()).isEmpty();
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(77_000L);
        assertThat(respuesta.listoParaAsignarAntes()).isEqualTo(77_000L);
    }
}
