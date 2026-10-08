package com.presupuesto.importacion.dto.response;

/**
 * Resultado de una importación: filas creadas, omitidas por duplicadas, inválidas omitidas
 * (solo con {@code omitirInvalidas}) y cuántas de las creadas quedaron sin categoría.
 */
public record ImportacionResponse(int importadas, int duplicadas, int omitidas, int sinCategoria) {

    public static ImportacionResponse desde(
            int importadas, int duplicadas, int omitidas, int sinCategoria) {
        return new ImportacionResponse(importadas, duplicadas, omitidas, sinCategoria);
    }
}
