package com.presupuesto.presupuesto.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PresupuestoTest {

    @Test
    void normalizarPasaAMinusculas() {
        assertThat(Presupuesto.normalizar("Casa Y VIAJES")).isEqualTo("casa y viajes");
    }

    @Test
    void normalizarNoDependeDelIdiomaDeLaJvm() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Presupuesto.normalizar("TITLE")).isEqualTo("title");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @Test
    void renombrarActualizaElNombreYSuFormaNormalizada() {
        Presupuesto presupuesto = Presupuesto.builder()
                .nombre("Casa")
                .nombreNormalizado("casa")
                .moneda("BOB")
                .build();

        presupuesto.renombrar("Hogar DULCE");

        assertThat(presupuesto.getNombre()).isEqualTo("Hogar DULCE");
        assertThat(presupuesto.getNombreNormalizado()).isEqualTo("hogar dulce");
        assertThat(presupuesto.getMoneda()).isEqualTo("BOB");
    }
}
