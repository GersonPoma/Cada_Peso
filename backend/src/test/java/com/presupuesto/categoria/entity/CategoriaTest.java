package com.presupuesto.categoria.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CategoriaTest {

    @Test
    void normalizarPasaAMinusculasSinDependerDelIdiomaDeLaJvm() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Categoria.normalizar("TITLE Alquiler")).isEqualTo("title alquiler");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @Test
    void renombrarActualizaElNombreYSuFormaNormalizada() {
        Categoria categoria =
                Categoria.builder().nombre("Luz").nombreNormalizado("luz").build();

        categoria.renombrar("Agua Potable");

        assertThat(categoria.getNombre()).isEqualTo("Agua Potable");
        assertThat(categoria.getNombreNormalizado()).isEqualTo("agua potable");
    }

    @Test
    void cambiarNotaYAsignarOrdenCambianSusCampos() {
        Categoria categoria = Categoria.builder().build();

        categoria.cambiarNota("Pago mensual");
        categoria.asignarOrden(3);
        assertThat(categoria.getNota()).isEqualTo("Pago mensual");
        assertThat(categoria.getOrden()).isEqualTo(3);

        categoria.cambiarNota(null);
        assertThat(categoria.getNota()).isNull();
    }

    @Test
    void moverAGrupoCambiaElGrupo() {
        GrupoCategoria origen = GrupoCategoria.builder().build();
        GrupoCategoria destino = GrupoCategoria.builder().build();
        Categoria categoria = Categoria.builder().grupo(origen).build();

        categoria.moverAGrupo(destino);

        assertThat(categoria.getGrupo()).isSameAs(destino);
    }

    @Test
    void ocultarYMostrarSonIdempotentes() {
        Categoria categoria = Categoria.builder().build();
        assertThat(categoria.isOculta()).isFalse();

        categoria.ocultar();
        categoria.ocultar();
        assertThat(categoria.isOculta()).isTrue();

        categoria.mostrar();
        categoria.mostrar();
        assertThat(categoria.isOculta()).isFalse();
    }

    @Test
    void noExistenLosSettersBloqueados() {
        assertThat(Arrays.stream(Categoria.class.getMethods()).map(Method::getName))
                .doesNotContain("setNombre", "setNombreNormalizado", "setOrden", "setOculta",
                        "setNota", "setGrupo");
    }
}
