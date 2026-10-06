package com.presupuesto.asignacion.dto.response;

import java.time.YearMonth;
import java.util.List;

/**
 * El presupuesto de un mes. Los totales suman las categorías incluidas en {@code grupos};
 * {@code listoParaAsignar} considera siempre todo el presupuesto.
 */
public record MesPresupuestoResponse(
        String mes,
        long listoParaAsignar,
        long totalAsignado,
        long totalActividad,
        long totalDisponible,
        List<GrupoMesResponse> grupos) {

    public static MesPresupuestoResponse desde(
            YearMonth mes, long listoParaAsignar, List<GrupoMesResponse> grupos) {
        long asignado = 0L;
        long actividad = 0L;
        long disponible = 0L;
        for (GrupoMesResponse grupo : grupos) {
            for (CategoriaMesResponse categoria : grupo.categorias()) {
                asignado += categoria.asignado();
                actividad += categoria.actividad();
                disponible += categoria.disponible();
            }
        }
        return new MesPresupuestoResponse(
                mes.toString(), listoParaAsignar, asignado, actividad, disponible, grupos);
    }
}
