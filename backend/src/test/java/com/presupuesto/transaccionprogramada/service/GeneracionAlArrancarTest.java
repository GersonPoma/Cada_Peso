package com.presupuesto.transaccionprogramada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.config.RelojDePrueba;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GeneracionAlArrancarTest {

    @Nested
    class Unitario {

        private final GeneradorProgramadas generador = mock(GeneradorProgramadas.class);
        private final RelojDePrueba reloj = new RelojDePrueba();

        @Test
        void generaLasVencidasDeTodosLosPresupuestosConLaFechaDeHoyDelReloj() {
            reloj.fijar(Instant.parse("2026-10-10T15:00:00Z"));
            when(generador.generarVencidas(LocalDate.of(2026, 10, 10)))
                    .thenReturn(new ResultadoGeneracion(2, 0));

            new GeneracionAlArrancar(generador, reloj).run(new DefaultApplicationArguments());

            verify(generador).generarVencidas(LocalDate.of(2026, 10, 10));
        }

        @Test
        void unFalloDelGeneradorNoTumbaElArranque() {
            when(generador.generarVencidas(LocalDate.of(2026, 10, 2)))
                    .thenThrow(new IllegalStateException("base caída"));

            assertThatCode(() -> new GeneracionAlArrancar(generador, reloj)
                    .run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        }
    }

    /** Contexto completo con el generador simulado: nunca toca datos reales. */
    @Nested
    @SpringBootTest(properties = "programadas.generacion.habilitada=true")
    class ConElGeneradorHabilitado {

        @MockitoBean
        private GeneradorProgramadas generador;

        @Autowired
        private GeneracionAlArrancar alArrancar;

        @Autowired
        private GeneracionDiaria diaria;

        @Test
        void alArrancarLaAplicacionSeGeneranLasVencidas() {
            assertThat(alArrancar).isNotNull();
            assertThat(diaria).isNotNull();
            verify(generador, times(1)).generarVencidas(org.mockito.ArgumentMatchers.any(
                    LocalDate.class));
        }
    }
}
