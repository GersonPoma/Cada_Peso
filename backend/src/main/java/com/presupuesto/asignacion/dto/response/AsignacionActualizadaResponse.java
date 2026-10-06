package com.presupuesto.asignacion.dto.response;

/** Resultado de asignar: la fila de la categoría ya calculada y el listo para asignar del mes. */
public record AsignacionActualizadaResponse(
        CategoriaMesResponse categoria, long listoParaAsignar) {

    public static AsignacionActualizadaResponse desde(
            CategoriaMesResponse categoria, long listoParaAsignar) {
        return new AsignacionActualizadaResponse(categoria, listoParaAsignar);
    }
}
