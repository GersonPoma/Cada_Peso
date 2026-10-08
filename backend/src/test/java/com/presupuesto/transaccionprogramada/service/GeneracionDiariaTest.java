package com.presupuesto.transaccionprogramada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.config.ProgramacionConfig;
import com.presupuesto.comun.config.RelojDePrueba;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

class GeneracionDiariaTest {

    private final GeneradorProgramadas generador = mock(GeneradorProgramadas.class);
    private final RelojDePrueba reloj = new RelojDePrueba();

    @Test
    void laTareaGeneraLasVencidasDeTodosLosPresupuestosConLaFechaDeHoy() {
        reloj.fijar(Instant.parse("2026-10-10T03:00:00Z"));
        when(generador.generarVencidas(LocalDate.of(2026, 10, 10)))
                .thenReturn(new ResultadoGeneracion(1, 0));

        new GeneracionDiaria(generador, reloj).ejecutar();

        verify(generador).generarVencidas(LocalDate.of(2026, 10, 10));
    }

    @Test
    void unFalloDelGeneradorNoPropagaLaExcepcionALaTarea() {
        when(generador.generarVencidas(LocalDate.of(2026, 10, 2)))
                .thenThrow(new IllegalStateException("base caída"));

        assertThatCode(() -> new GeneracionDiaria(generador, reloj).ejecutar())
                .doesNotThrowAnyException();
    }

    @Test
    void laHoraSeConfiguraPorPropiedadConUnCronPorDefectoALas3EnUtc() throws Exception {
        Method ejecutar = GeneracionDiaria.class.getDeclaredMethod("ejecutar");
        Scheduled scheduled = ejecutar.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${programadas.generacion.cron:0 0 3 * * *}");
        assertThat(scheduled.zone()).isEqualTo("UTC");
    }

    @Test
    void laConfiguracionActivaLasTareasProgramadas() {
        assertThat(ProgramacionConfig.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}
