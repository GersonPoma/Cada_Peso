package com.presupuesto.categoria.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class GrupoCategoriaTest {

    @Test
    void normalizarPasaAMinusculasSinDependerDelIdiomaDeLaJvm() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(GrupoCategoria.normalizar("TITLE Vivienda")).isEqualTo("title vivienda");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @Test
    void renombrarActualizaElNombreYSuFormaNormalizada() {
        GrupoCategoria grupo =
                GrupoCategoria.builder().nombre("Hogar").nombreNormalizado("hogar").build();

        grupo.renombrar("Vida Diaria");

        assertThat(grupo.getNombre()).isEqualTo("Vida Diaria");
        assertThat(grupo.getNombreNormalizado()).isEqualTo("vida diaria");
    }

    @Test
    void asignarOrdenCambiaElOrden() {
        GrupoCategoria grupo = GrupoCategoria.builder().build();

        grupo.asignarOrden(4);

        assertThat(grupo.getOrden()).isEqualTo(4);
    }

    @Test
    void ocultarYMostrarSonIdempotentes() {
        GrupoCategoria grupo = GrupoCategoria.builder().build();
        assertThat(grupo.isOculto()).isFalse();

        grupo.ocultar();
        grupo.ocultar();
        assertThat(grupo.isOculto()).isTrue();

        grupo.mostrar();
        grupo.mostrar();
        assertThat(grupo.isOculto()).isFalse();
    }

    @Test
    void noExistenLosSettersBloqueados() {
        assertThat(Arrays.stream(GrupoCategoria.class.getMethods()).map(Method::getName))
                .doesNotContain("setNombre", "setNombreNormalizado", "setOrden", "setOculto");
    }

    @Test
    void elTipoPorDefectoEsNormalYNoTieneSetter() {
        GrupoCategoria grupo = GrupoCategoria.builder().build();
        GrupoCategoria pagos =
                GrupoCategoria.builder().tipo(TipoGrupoCategoria.PAGOS_TARJETA).build();

        assertThat(grupo.getTipo()).isEqualTo(TipoGrupoCategoria.NORMAL);
        assertThat(grupo.esPagosTarjeta()).isFalse();
        assertThat(pagos.esPagosTarjeta()).isTrue();
        assertThat(Arrays.stream(GrupoCategoria.class.getMethods()).map(Method::getName))
                .doesNotContain("setTipo");
    }
}
