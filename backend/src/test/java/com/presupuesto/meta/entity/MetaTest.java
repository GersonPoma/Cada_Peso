package com.presupuesto.meta.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MetaTest {

    private static Meta nueva() {
        return Meta.builder()
                .categoria(Categoria.builder().id(1L).build())
                .tipo(TipoMeta.MONTO_MENSUAL)
                .monto(100_000L)
                .frecuencia(FrecuenciaMeta.MENSUAL)
                .build();
    }

    @Test
    void reemplazarCambiaTodosLosDatosYConservaLaCategoria() {
        Meta meta = nueva();
        Categoria categoria = meta.getCategoria();

        meta.reemplazar(TipoMeta.MONTO_PARA_FECHA, 600_000L, null, null, null, null,
                LocalDate.of(2026, 12, 15));

        assertThat(meta.getTipo()).isEqualTo(TipoMeta.MONTO_PARA_FECHA);
        assertThat(meta.getMonto()).isEqualTo(600_000L);
        assertThat(meta.getFrecuencia()).isNull();
        assertThat(meta.getFechaObjetivo()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(meta.getCategoria()).isSameAs(categoria);
    }

    @Test
    void reemplazarAdmiteTodosLosCamposDeUnaMetaPersonalizada() {
        Meta meta = nueva();

        meta.reemplazar(TipoMeta.MONTO_MENSUAL, 10_000L, FrecuenciaMeta.PERSONALIZADA, null, 14,
                LocalDate.of(2026, 10, 2), null);

        assertThat(meta.getFrecuencia()).isEqualTo(FrecuenciaMeta.PERSONALIZADA);
        assertThat(meta.getIntervaloDias()).isEqualTo(14);
        assertThat(meta.getFechaInicio()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(meta.getDiaSemana()).isNull();
    }

    @Test
    void noExponeSetters() {
        assertThat(Arrays.stream(Meta.class.getDeclaredMethods())
                        .map(Method::getName)
                        .filter(nombre -> nombre.startsWith("set")))
                .isEmpty();
    }

    @Test
    void losEnumsTienenLosValoresDeLaApi() {
        assertThat(TipoMeta.values()).extracting(Enum::name).containsExactly(
                "MONTO_MENSUAL", "MONTO_PARA_FECHA", "SALDO_OBJETIVO");
        assertThat(FrecuenciaMeta.values()).extracting(Enum::name).containsExactly(
                "SEMANAL", "MENSUAL", "PERSONALIZADA");
    }
}
