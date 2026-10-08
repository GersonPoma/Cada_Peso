package com.presupuesto.transaccionprogramada.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransaccionProgramadaResponseTest {

    private static final Instant CREADA = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant ACTUALIZADA = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void mapeaTodosLosCampos() {
        TransaccionProgramada programada = TransaccionProgramada.builder()
                .id(4L)
                .cuenta(Cuenta.builder().id(3L).build())
                .fechaInicio(LocalDate.of(2026, 1, 31))
                .frecuencia(FrecuenciaProgramada.MENSUAL)
                .fechaFin(LocalDate.of(2027, 1, 31))
                .monto(-5000L)
                .categoria(Categoria.builder().id(7L).build())
                .beneficiario("Casero")
                .memo("renta")
                .proximaFecha(LocalDate.of(2026, 2, 28))
                .ultimoError("La cuenta está cerrada")
                .fechaCreacion(CREADA)
                .fechaActualizacion(ACTUALIZADA)
                .build();

        TransaccionProgramadaResponse respuesta = TransaccionProgramadaResponse.desde(programada);

        assertThat(respuesta.id()).isEqualTo(4L);
        assertThat(respuesta.cuentaId()).isEqualTo(3L);
        assertThat(respuesta.fechaInicio()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(respuesta.frecuencia()).isEqualTo(FrecuenciaProgramada.MENSUAL);
        assertThat(respuesta.fechaFin()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(respuesta.monto()).isEqualTo(-5000L);
        assertThat(respuesta.categoriaId()).isEqualTo(7L);
        assertThat(respuesta.beneficiario()).isEqualTo("Casero");
        assertThat(respuesta.memo()).isEqualTo("renta");
        assertThat(respuesta.activa()).isTrue();
        assertThat(respuesta.proximaFecha()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(respuesta.ultimoError()).isEqualTo("La cuenta está cerrada");
        assertThat(respuesta.fechaCreacion()).isEqualTo(CREADA);
        assertThat(respuesta.fechaActualizacion()).isEqualTo(ACTUALIZADA);
    }

    @Test
    void sinCategoriaNiFinLosDevuelveNulos() {
        TransaccionProgramada programada = TransaccionProgramada.builder()
                .id(5L)
                .cuenta(Cuenta.builder().id(3L).build())
                .fechaInicio(LocalDate.of(2026, 10, 1))
                .frecuencia(FrecuenciaProgramada.DIARIA)
                .monto(100L)
                .activa(false)
                .build();

        TransaccionProgramadaResponse respuesta = TransaccionProgramadaResponse.desde(programada);

        assertThat(respuesta.categoriaId()).isNull();
        assertThat(respuesta.fechaFin()).isNull();
        assertThat(respuesta.proximaFecha()).isNull();
        assertThat(respuesta.ultimoError()).isNull();
        assertThat(respuesta.activa()).isFalse();
    }
}
