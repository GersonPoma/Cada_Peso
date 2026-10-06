package com.presupuesto.cuenta.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CuentaTest {

    @Test
    void normalizarPasaAMinusculasSinDependerDelIdiomaDeLaJvm() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Cuenta.normalizar("TITLE Banco")).isEqualTo("title banco");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @Test
    void renombrarActualizaElNombreYSuFormaNormalizada() {
        Cuenta cuenta = Cuenta.builder().nombre("Banco").nombreNormalizado("banco").build();

        cuenta.renombrar("Ahorros BCP");

        assertThat(cuenta.getNombre()).isEqualTo("Ahorros BCP");
        assertThat(cuenta.getNombreNormalizado()).isEqualTo("ahorros bcp");
    }

    @Test
    void cambiarTipoCambiaElTipo() {
        Cuenta cuenta = Cuenta.builder().tipo(TipoCuenta.CORRIENTE).build();

        cuenta.cambiarTipo(TipoCuenta.AHORRO);

        assertThat(cuenta.getTipo()).isEqualTo(TipoCuenta.AHORRO);
    }

    @Test
    void cerrarYReabrirSonIdempotentes() {
        Cuenta cuenta = Cuenta.builder().build();
        assertThat(cuenta.isCerrada()).isFalse();

        cuenta.cerrar();
        cuenta.cerrar();
        assertThat(cuenta.isCerrada()).isTrue();

        cuenta.reabrir();
        cuenta.reabrir();
        assertThat(cuenta.isCerrada()).isFalse();
    }

    @Test
    void elBuilderUsaLosValoresPorDefecto() {
        Cuenta cuenta = Cuenta.builder().build();

        assertThat(cuenta.isEnPresupuesto()).isTrue();
        assertThat(cuenta.getSaldoInicial()).isZero();
        assertThat(cuenta.isCerrada()).isFalse();
    }

    @Test
    void losCamposProtegidosNoTienenSetter() {
        assertThat(Arrays.stream(Cuenta.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain(
                        "setNombre",
                        "setNombreNormalizado",
                        "setTipo",
                        "setEnPresupuesto",
                        "setSaldoInicial",
                        "setCerrada");
    }
}
