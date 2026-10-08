package com.presupuesto.importacion.dto.response;

import java.util.List;

/**
 * Totales de la vista previa. {@code sinCategoria} es cuántas transacciones quedarían sin
 * categoría: hoy toda fila nueva nace sin ella, así que coincide con {@code nuevas}.
 */
public record TotalesResponse(
        int total, int nuevas, int duplicadas, int invalidas, int sinCategoria) {

    public static TotalesResponse desde(List<FilaPreviaResponse> filas) {
        int nuevas = contar(filas, EstadoFila.NUEVA);
        return new TotalesResponse(
                filas.size(),
                nuevas,
                contar(filas, EstadoFila.DUPLICADA),
                contar(filas, EstadoFila.INVALIDA),
                nuevas);
    }

    private static int contar(List<FilaPreviaResponse> filas, EstadoFila estado) {
        return (int) filas.stream().filter(fila -> fila.estado() == estado).count();
    }
}
