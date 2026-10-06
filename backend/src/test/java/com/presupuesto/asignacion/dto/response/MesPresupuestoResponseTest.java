package com.presupuesto.asignacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class MesPresupuestoResponseTest {

    @Test
    void sumaLasCategoriasIncluidasYFormateaElMes() {
        GrupoMesResponse grupo = new GrupoMesResponse(1L, "Hogar", 0, false, List.of(
                new CategoriaMesResponse(1L, "Comida", false, 100_000L, -30_000L, 70_000L, false),
                new CategoriaMesResponse(2L, "Ocio", false, 20_000L, 0L, 20_000L, false)));
        GrupoMesResponse otro = new GrupoMesResponse(2L, "Otro", 1, false, List.of(
                new CategoriaMesResponse(3L, "Extra", false, 0L, -5_000L, -5_000L, true)));

        MesPresupuestoResponse respuesta = MesPresupuestoResponse.desde(
                YearMonth.of(2026, 1), 380_000L, List.of(grupo, otro));

        assertThat(respuesta.mes()).isEqualTo("2026-01");
        assertThat(respuesta.listoParaAsignar()).isEqualTo(380_000L);
        assertThat(respuesta.totalAsignado()).isEqualTo(120_000L);
        assertThat(respuesta.totalActividad()).isEqualTo(-35_000L);
        assertThat(respuesta.totalDisponible()).isEqualTo(85_000L);
        assertThat(respuesta.grupos()).containsExactly(grupo, otro);
    }

    @Test
    void sinGruposLosTotalesSonCeroYElListoSeConserva() {
        MesPresupuestoResponse respuesta =
                MesPresupuestoResponse.desde(YearMonth.of(2030, 12), -50_000L, List.of());

        assertThat(respuesta.mes()).isEqualTo("2030-12");
        assertThat(respuesta.listoParaAsignar()).isEqualTo(-50_000L);
        assertThat(respuesta.totalAsignado()).isZero();
        assertThat(respuesta.totalActividad()).isZero();
        assertThat(respuesta.totalDisponible()).isZero();
    }
}
