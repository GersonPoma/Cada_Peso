package com.presupuesto.importacion.service;

import com.presupuesto.transaccion.service.ClaveMovimiento;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decide qué filas del archivo son duplicadas contando ocurrencias: si la cuenta ya tiene
 * {@code E} movimientos con una clave, las primeras {@code E} filas del archivo con esa clave (en
 * orden de archivo) son duplicadas y el resto nuevas. Tras importar, la cuenta queda con
 * {@code max(E, F)}, así que reimportar crea 0.
 */
final class ClasificadorDuplicados {

    private ClasificadorDuplicados() {}

    /**
     * @param claves una por fila del archivo, en orden; {@code null} para las filas inválidas, que
     *     no participan
     * @return para cada fila, {@code true} si es duplicada (siempre {@code false} si su clave es
     *     {@code null})
     */
    static List<Boolean> clasificar(
            List<ClaveMovimiento> claves, Map<ClaveMovimiento, Integer> existentes) {
        Map<ClaveMovimiento, Integer> vistas = new HashMap<>();
        List<Boolean> duplicadas = new ArrayList<>(claves.size());
        for (ClaveMovimiento clave : claves) {
            if (clave == null) {
                duplicadas.add(false);
                continue;
            }
            int anteriores = vistas.merge(clave, 1, Integer::sum);
            duplicadas.add(anteriores <= existentes.getOrDefault(clave, 0));
        }
        return duplicadas;
    }
}
