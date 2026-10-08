package com.presupuesto.importacion.dto.response;

import java.util.List;

public record VistaPreviaResponse(TotalesResponse totales, List<FilaPreviaResponse> filas) {

    public static VistaPreviaResponse desde(List<FilaPreviaResponse> filas) {
        return new VistaPreviaResponse(TotalesResponse.desde(filas), filas);
    }
}
