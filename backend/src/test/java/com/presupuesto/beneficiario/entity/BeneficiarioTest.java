package com.presupuesto.beneficiario.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import org.junit.jupiter.api.Test;

class BeneficiarioTest {

    @Test
    void normalizarPasaAMinusculasSinDependerDelIdiomaDeLaJvm() {
        assertThat(Beneficiario.normalizar("NETFLIX Bolivia")).isEqualTo("netflix bolivia");
        assertThat(Beneficiario.normalizar("TITLE")).isEqualTo("title");
    }

    @Test
    void renombrarMantieneSincronizadoElNombreNormalizado() {
        Beneficiario beneficiario = Beneficiario.builder()
                .nombre("netflix").nombreNormalizado("netflix").build();

        beneficiario.renombrar("Netflix Bolivia");

        assertThat(beneficiario.getNombre()).isEqualTo("Netflix Bolivia");
        assertThat(beneficiario.getNombreNormalizado()).isEqualTo("netflix bolivia");
    }

    @Test
    void sinCategoriaPredeterminadaPorDefecto() {
        assertThat(Beneficiario.builder().build().getCategoriaPredeterminada()).isNull();
    }

    @Test
    void recordarCategoriaReemplazaLaAnterior() {
        Categoria ocio = Categoria.builder().id(1L).build();
        Categoria comida = Categoria.builder().id(2L).build();
        Beneficiario beneficiario = Beneficiario.builder().categoriaPredeterminada(ocio).build();

        beneficiario.recordarCategoria(comida);

        assertThat(beneficiario.getCategoriaPredeterminada()).isSameAs(comida);
    }

    @Test
    void cambiarCategoriaPredeterminadaAdmiteNuloParaQuitarla() {
        Categoria ocio = Categoria.builder().id(1L).build();
        Beneficiario beneficiario = Beneficiario.builder().categoriaPredeterminada(ocio).build();

        beneficiario.cambiarCategoriaPredeterminada(null);

        assertThat(beneficiario.getCategoriaPredeterminada()).isNull();
    }

    @Test
    void losCamposDerivadosNoTienenSetterPublico() {
        for (String metodo : new String[] {
                "setNombre", "setNombreNormalizado", "setCategoriaPredeterminada"}) {
            boolean existe = java.util.Arrays.stream(Beneficiario.class.getMethods())
                    .anyMatch(m -> m.getName().equals(metodo));
            assertThat(existe).as(metodo).isFalse();
        }
    }
}
