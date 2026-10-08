package com.presupuesto.transaccionprogramada.service;

import static com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada.ANUAL;
import static com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada.CADA_2_SEMANAS;
import static com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada.CADA_3_MESES;
import static com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada.DIARIA;
import static com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada.MENSUAL;
import static com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada.SEMANAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarioProgramadoTest {

    private static LocalDate f(int anio, int mes, int dia) {
        return LocalDate.of(anio, mes, dia);
    }

    private static List<LocalDate> primeras(
            LocalDate inicio, FrecuenciaProgramada frecuencia, int n) {
        List<LocalDate> fechas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fechas.add(CalendarioProgramado.ocurrencia(inicio, frecuencia, i));
        }
        return fechas;
    }

    @Test
    void mensualDesdeElDia31SeAjustaAlUltimoDiaYRecuperaElDia31() {
        assertThat(primeras(f(2027, 1, 31), MENSUAL, 5)).containsExactly(
                f(2027, 1, 31), f(2027, 2, 28), f(2027, 3, 31), f(2027, 4, 30), f(2027, 5, 31));
    }

    @Test
    void mensualDesdeElDia31EnAnioBisiestoUsaElDia29DeFebrero() {
        assertThat(primeras(f(2028, 1, 31), MENSUAL, 4)).containsExactly(
                f(2028, 1, 31), f(2028, 2, 29), f(2028, 3, 31), f(2028, 4, 30));
    }

    @Test
    void mensualDesdeElDia29Y30() {
        assertThat(primeras(f(2027, 1, 29), MENSUAL, 3))
                .containsExactly(f(2027, 1, 29), f(2027, 2, 28), f(2027, 3, 29));
        assertThat(primeras(f(2027, 1, 30), MENSUAL, 3))
                .containsExactly(f(2027, 1, 30), f(2027, 2, 28), f(2027, 3, 30));
        assertThat(primeras(f(2028, 1, 29), MENSUAL, 2))
                .containsExactly(f(2028, 1, 29), f(2028, 2, 29));
    }

    @Test
    void ocurrenciaDistanteNoSeDegradaPorLaCadena() {
        // 31-ene + 25 meses = 28-feb-2029; + 26 = 31-mar-2029 (no 28-mar).
        assertThat(CalendarioProgramado.ocurrencia(f(2027, 1, 31), MENSUAL, 25))
                .isEqualTo(f(2029, 2, 28));
        assertThat(CalendarioProgramado.ocurrencia(f(2027, 1, 31), MENSUAL, 26))
                .isEqualTo(f(2029, 3, 31));
    }

    @Test
    void cadaTresMesesDesdeElDia31DeEnero() {
        assertThat(primeras(f(2027, 1, 31), CADA_3_MESES, 4)).containsExactly(
                f(2027, 1, 31), f(2027, 4, 30), f(2027, 7, 31), f(2027, 10, 31));
    }

    @Test
    void cadaTresMesesDesdeElDia30DeNoviembre() {
        assertThat(primeras(f(2027, 11, 30), CADA_3_MESES, 3))
                .containsExactly(f(2027, 11, 30), f(2028, 2, 29), f(2028, 5, 30));
    }

    @Test
    void anualDesdeElDia29DeFebreroDe2028() {
        assertThat(primeras(f(2028, 2, 29), ANUAL, 5)).containsExactly(
                f(2028, 2, 29), f(2029, 2, 28), f(2030, 2, 28), f(2031, 2, 28), f(2032, 2, 29));
    }

    @Test
    void diariaSemanalYCadaDosSemanas() {
        LocalDate inicio = f(2026, 10, 1);
        assertThat(primeras(inicio, DIARIA, 2)).containsExactly(f(2026, 10, 1), f(2026, 10, 2));
        assertThat(primeras(inicio, SEMANAL, 2)).containsExactly(f(2026, 10, 1), f(2026, 10, 8));
        assertThat(primeras(inicio, CADA_2_SEMANAS, 2))
                .containsExactly(f(2026, 10, 1), f(2026, 10, 15));
    }

    @Test
    void primeraDesdeConFechaAnteriorOIgualAlInicioDevuelveElInicio() {
        assertThat(CalendarioProgramado.primeraDesde(f(2026, 10, 5), MENSUAL, f(2026, 1, 1)))
                .isEqualTo(f(2026, 10, 5));
        assertThat(CalendarioProgramado.primeraDesde(f(2026, 10, 5), MENSUAL, f(2026, 10, 5)))
                .isEqualTo(f(2026, 10, 5));
    }

    @Test
    void primeraDesdeIncluyeUnaFechaQueEsOcurrenciaYSaltaSiEstaEntreDos() {
        LocalDate inicio = f(2027, 1, 31);
        assertThat(CalendarioProgramado.primeraDesde(inicio, MENSUAL, f(2027, 2, 28)))
                .isEqualTo(f(2027, 2, 28));
        assertThat(CalendarioProgramado.primeraDesde(inicio, MENSUAL, f(2027, 3, 1)))
                .isEqualTo(f(2027, 3, 31));
        assertThat(CalendarioProgramado.primeraDesde(inicio, MENSUAL, f(2027, 4, 30)))
                .isEqualTo(f(2027, 4, 30));
    }

    @Test
    void primeraDespuesDeEsEstrictamentePosterior() {
        LocalDate inicio = f(2027, 1, 31);
        assertThat(CalendarioProgramado.primeraDespuesDe(inicio, MENSUAL, f(2027, 2, 28)))
                .isEqualTo(f(2027, 3, 31));
        assertThat(CalendarioProgramado.primeraDespuesDe(inicio, MENSUAL, f(2027, 2, 27)))
                .isEqualTo(f(2027, 2, 28));
        assertThat(CalendarioProgramado.primeraDespuesDe(inicio, MENSUAL, f(2020, 1, 1)))
                .isEqualTo(inicio);
    }

    @Test
    void primeraDespuesDeSemanalDesde15DeEneroDa17DeSeptiembre() {
        // 2026-01-15 es jueves; el 15 de septiembre es martes: el siguiente jueves es el 17.
        assertThat(CalendarioProgramado.primeraDespuesDe(f(2026, 1, 15), SEMANAL, f(2026, 9, 15)))
                .isEqualTo(f(2026, 9, 17));
    }

    @Test
    void primeraDesdeConMuchosAniosDeDistanciaNoRecorreUnoAUno() {
        assertThat(CalendarioProgramado.primeraDesde(f(2000, 1, 31), DIARIA, f(2100, 6, 15)))
                .isEqualTo(f(2100, 6, 15));
        assertThat(CalendarioProgramado.primeraDesde(f(2000, 1, 31), MENSUAL, f(2100, 2, 1)))
                .isEqualTo(f(2100, 2, 28));
        assertThat(CalendarioProgramado.primeraDesde(f(2000, 2, 29), ANUAL, f(2100, 1, 1)))
                .isEqualTo(f(2100, 2, 28));
    }

    @Test
    void conCadaFrecuenciaLaPrimeraDesdeNuncaEsAnteriorNiSalteaUnaOcurrencia() {
        LocalDate inicio = f(2027, 1, 31);
        for (FrecuenciaProgramada frecuencia : FrecuenciaProgramada.values()) {
            for (LocalDate fecha = f(2027, 1, 1); fecha.isBefore(f(2029, 3, 1));
                    fecha = fecha.plusDays(1)) {
                LocalDate resultado = CalendarioProgramado.primeraDesde(inicio, frecuencia, fecha);
                assertThat(resultado).isAfterOrEqualTo(fecha);
                long n = 0;
                while (CalendarioProgramado.ocurrencia(inicio, frecuencia, n).isBefore(fecha)) {
                    n++;
                }
                assertThat(resultado)
                        .isEqualTo(CalendarioProgramado.ocurrencia(inicio, frecuencia, n));
            }
        }
    }
}
