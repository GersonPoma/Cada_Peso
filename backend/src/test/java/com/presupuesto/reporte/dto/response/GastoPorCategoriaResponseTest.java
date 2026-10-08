package com.presupuesto.reporte.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import java.time.YearMonth;
import java.util.List;
import java.util.function.LongUnaryOperator;
import org.junit.jupiter.api.Test;

class GastoPorCategoriaResponseTest {

    private static final LongUnaryOperator MITAD = total -> total / 2;

    private static Categoria categoria(long id, String nombre, boolean oculta) {
        Categoria categoria = Categoria.builder().nombre(nombre).oculta(oculta).build();
        categoria.setId(id);
        return categoria;
    }

    private static GrupoCategoria grupo(long id, String nombre) {
        GrupoCategoria grupo = GrupoCategoria.builder().nombre(nombre).build();
        grupo.setId(id);
        return grupo;
    }

    @Test
    void laCategoriaTraSuTotalYSuPorcentaje() {
        CategoriaGastoResponse respuesta =
                CategoriaGastoResponse.desde(categoria(7L, "Comida", true), 140_000L, MITAD);

        assertThat(respuesta.categoriaId()).isEqualTo(7L);
        assertThat(respuesta.nombre()).isEqualTo("Comida");
        assertThat(respuesta.oculta()).isTrue();
        assertThat(respuesta.total()).isEqualTo(140_000L);
        assertThat(respuesta.porcentaje()).isEqualTo(70_000L);
    }

    @Test
    void elTotalDeUnGrupoEsLaSumaDeSusCategorias() {
        GrupoGastoResponse respuesta = GrupoGastoResponse.desde(grupo(1L, "Vida diaria"), List.of(
                CategoriaGastoResponse.desde(categoria(7L, "Comida", false), 140_000L, MITAD),
                CategoriaGastoResponse.desde(categoria(8L, "Hogar", false), 20_000L, MITAD)),
                MITAD);

        assertThat(respuesta.total()).isEqualTo(160_000L);
        assertThat(respuesta.porcentaje()).isEqualTo(80_000L);
        assertThat(respuesta.categorias()).hasSize(2);
    }

    @Test
    void elTotalGeneralSumaLosGruposYElCuboSinCategoria() {
        GrupoGastoResponse vida = GrupoGastoResponse.desde(grupo(1L, "Vida diaria"), List.of(
                CategoriaGastoResponse.desde(categoria(7L, "Comida", false), 140_000L, MITAD)),
                MITAD);
        GrupoGastoResponse ahorro = GrupoGastoResponse.desde(grupo(2L, "Ahorro"), List.of(
                CategoriaGastoResponse.desde(
                        categoria(9L, "Metas de ahorro", false), 100_000L, MITAD)),
                MITAD);

        GastoPorCategoriaResponse respuesta = GastoPorCategoriaResponse.desde(
                YearMonth.of(2026, 10), YearMonth.of(2026, 10), List.of(vida, ahorro),
                SinCategoriaGastoResponse.desde(5_000L, MITAD));

        assertThat(respuesta.desde()).isEqualTo("2026-10");
        assertThat(respuesta.hasta()).isEqualTo("2026-10");
        assertThat(respuesta.total()).isEqualTo(245_000L);
        assertThat(respuesta.sinCategoria().total()).isEqualTo(5_000L);
    }

    @Test
    void sinMovimientosElTotalEsCeroYNoHayGrupos() {
        GastoPorCategoriaResponse respuesta = GastoPorCategoriaResponse.desde(
                YearMonth.of(2026, 1), YearMonth.of(2026, 3), List.of(),
                SinCategoriaGastoResponse.desde(0L, MITAD));

        assertThat(respuesta.total()).isZero();
        assertThat(respuesta.grupos()).isEmpty();
    }
}
