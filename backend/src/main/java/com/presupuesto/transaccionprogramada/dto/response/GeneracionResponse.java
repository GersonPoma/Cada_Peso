package com.presupuesto.transaccionprogramada.dto.response;

import com.presupuesto.transaccionprogramada.service.ResultadoGeneracion;

/** Cuántas transacciones creó una ejecución del generador y cuántas plantillas fallaron. */
public record GeneracionResponse(int generadas, int plantillasConError) {

    public static GeneracionResponse desde(ResultadoGeneracion resultado) {
        return new GeneracionResponse(resultado.generadas(), resultado.plantillasConError());
    }
}
