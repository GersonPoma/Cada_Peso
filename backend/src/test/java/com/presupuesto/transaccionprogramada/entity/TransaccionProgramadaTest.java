package com.presupuesto.transaccionprogramada.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransaccionProgramadaTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 31);

    private TransaccionProgramada nueva() {
        return TransaccionProgramada.builder()
                .fechaInicio(INICIO)
                .frecuencia(FrecuenciaProgramada.MENSUAL)
                .monto(-1000L)
                .proximaFecha(INICIO)
                .build();
    }

    @Test
    void nacePorDefectoActivaYSinError() {
        TransaccionProgramada programada = nueva();

        assertThat(programada.isActiva()).isTrue();
        assertThat(programada.getUltimoError()).isNull();
        assertThat(programada.getUltimaOcurrenciaGenerada()).isNull();
    }

    @Test
    void editarCambiaSoloLoQueAfectaAlFuturo() {
        TransaccionProgramada programada = nueva();
        Categoria comida = Categoria.builder().id(1L).build();

        programada.editar(-2000L, comida, "Casero", "nota", FrecuenciaProgramada.SEMANAL,
                LocalDate.of(2027, 1, 31));

        assertThat(programada.getMonto()).isEqualTo(-2000L);
        assertThat(programada.getCategoria()).isSameAs(comida);
        assertThat(programada.getBeneficiario()).isEqualTo("Casero");
        assertThat(programada.getMemo()).isEqualTo("nota");
        assertThat(programada.getFrecuencia()).isEqualTo(FrecuenciaProgramada.SEMANAL);
        assertThat(programada.getFechaFin()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(programada.getFechaInicio()).isEqualTo(INICIO);
    }

    @Test
    void pausarYActivarFijanLaProximaFechaYLimpianElError() {
        TransaccionProgramada programada = nueva();
        programada.registrarError("La cuenta está cerrada");

        programada.pausar();
        assertThat(programada.isActiva()).isFalse();
        assertThat(programada.getUltimoError()).isNotNull();

        programada.activar(LocalDate.of(2026, 11, 30));
        assertThat(programada.isActiva()).isTrue();
        assertThat(programada.getProximaFecha()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(programada.getUltimoError()).isNull();
    }

    @Test
    void registrarGeneracionAvanzaYBorraElError() {
        TransaccionProgramada programada = nueva();
        programada.registrarError("fallo");

        programada.registrarGeneracion(INICIO, LocalDate.of(2026, 2, 28));

        assertThat(programada.getUltimaOcurrenciaGenerada()).isEqualTo(INICIO);
        assertThat(programada.getProximaFecha()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(programada.getUltimoError()).isNull();
    }

    @Test
    void registrarGeneracionSinOcurrenciaConservaLaUltimaYPuedeFinalizar() {
        TransaccionProgramada programada = nueva();
        programada.registrarGeneracion(INICIO, LocalDate.of(2026, 2, 28));

        programada.registrarGeneracion(null, null);

        assertThat(programada.getUltimaOcurrenciaGenerada()).isEqualTo(INICIO);
        assertThat(programada.getProximaFecha()).isNull();
    }

    @Test
    void registrarErrorRecortaElMotivoA500Caracteres() {
        TransaccionProgramada programada = nueva();

        programada.registrarError("e".repeat(600));

        assertThat(programada.getUltimoError()).hasSize(500);
    }

    @Test
    void reprogramarCambiaLaProximaFecha() {
        TransaccionProgramada programada = nueva();

        programada.reprogramar(null);

        assertThat(programada.getProximaFecha()).isNull();
    }
}
