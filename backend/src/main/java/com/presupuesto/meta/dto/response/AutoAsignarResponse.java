package com.presupuesto.meta.dto.response;

import java.util.List;

/**
 * Resultado de auto-asignar. {@code listoParaAsignarDespues} es el de antes menos la suma de las
 * diferencias de {@code cambios}; {@code aplicado} es falso al simular.
 */
public record AutoAsignarResponse(
        boolean aplicado,
        long listoParaAsignarAntes,
        long listoParaAsignarDespues,
        List<CambioAsignacionResponse> cambios) {

    public static AutoAsignarResponse desde(
            boolean aplicado, long listoParaAsignarAntes, List<CambioAsignacionResponse> cambios) {
        long diferencia = cambios.stream()
                .mapToLong(cambio -> cambio.asignadoDespues() - cambio.asignadoAntes())
                .sum();
        return new AutoAsignarResponse(
                aplicado, listoParaAsignarAntes, listoParaAsignarAntes - diferencia, cambios);
    }
}
