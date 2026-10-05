package com.presupuesto.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MayorDeEdadValidatorTest {

    @Test
    void aceptaAQuienCumple18Hoy() {
        assertThat(validadorConHoy("2026-10-02").isValid(LocalDate.parse("2008-10-02"), null))
                .isTrue();
    }

    @Test
    void rechazaAQuienCumple18Manana() {
        assertThat(validadorConHoy("2026-10-02").isValid(LocalDate.parse("2008-10-03"), null))
                .isFalse();
    }

    @Test
    void rechazaANacidaUn29DeFebreroEl28DeFebreroDeUnAnioNoBisiesto() {
        assertThat(validadorConHoy("2026-02-28").isValid(LocalDate.parse("2008-02-29"), null))
                .isFalse();
    }

    @Test
    void aceptaANacidaUn29DeFebreroEl1DeMarzoDeUnAnioNoBisiesto() {
        assertThat(validadorConHoy("2026-03-01").isValid(LocalDate.parse("2008-02-29"), null))
                .isTrue();
    }

    @Test
    void rechazaUnaFechaDeNacimientoFutura() {
        assertThat(validadorConHoy("2026-10-02").isValid(LocalDate.parse("2030-01-01"), null))
                .isFalse();
    }

    @Test
    void aceptaNull() {
        assertThat(validadorConHoy("2026-10-02").isValid(null, null)).isTrue();
    }

    private static MayorDeEdadValidator validadorConHoy(String hoy) {
        Clock reloj = Clock.fixed(
                LocalDate.parse(hoy).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        return new MayorDeEdadValidator(reloj);
    }
}
