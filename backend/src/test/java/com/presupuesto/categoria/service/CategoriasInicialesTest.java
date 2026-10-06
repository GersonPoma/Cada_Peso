package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategoriasInicialesTest {

    @Test
    void tieneLosCuatroGruposEnOrden() {
        assertThat(CategoriasIniciales.ARBOL.keySet())
                .containsExactly("Facturas", "Necesidades", "Deseos", "Ahorro");
    }

    @Test
    void tieneLasCategoriasEnOrden() {
        assertThat(CategoriasIniciales.ARBOL.get("Facturas"))
                .containsExactly("Alquiler", "Luz", "Agua", "Internet", "Teléfono");
        assertThat(CategoriasIniciales.ARBOL.get("Necesidades"))
                .containsExactly("Comida", "Transporte", "Salud");
        assertThat(CategoriasIniciales.ARBOL.get("Deseos"))
                .containsExactly("Restaurantes", "Ocio", "Ropa");
        assertThat(CategoriasIniciales.ARBOL.get("Ahorro"))
                .containsExactly("Fondo de emergencia", "Vacaciones");
    }

    @Test
    void noRepiteGruposNiCategoriasDentroDeUnGrupo() {
        HashSet<String> grupos = new HashSet<>();
        for (Map.Entry<String, List<String>> entrada : CategoriasIniciales.ARBOL.entrySet()) {
            assertThat(grupos.add(entrada.getKey().toLowerCase(Locale.ROOT))).isTrue();
            HashSet<String> nombres = new HashSet<>();
            for (String nombre : entrada.getValue()) {
                assertThat(nombres.add(nombre.toLowerCase(Locale.ROOT))).isTrue();
            }
        }
    }

    @Test
    void todosLosNombresTienenEntreUnoYCienCaracteres() {
        CategoriasIniciales.ARBOL.forEach((grupo, categorias) -> {
            assertThat(grupo).hasSizeBetween(1, 100);
            categorias.forEach(nombre -> assertThat(nombre).hasSizeBetween(1, 100));
        });
    }

    @Test
    void esInmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> CategoriasIniciales.ARBOL.put("Otro", List.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> CategoriasIniciales.ARBOL.get("Ahorro").add("Otra"));
    }
}
