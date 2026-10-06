package com.presupuesto.categoria.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Árbol con el que nace todo presupuesto: grupo → categorías. El orden de inserción es el orden
 * de creación (el {@code orden} de cada grupo y categoría sale de su posición, desde 0).
 */
final class CategoriasIniciales {

    static final Map<String, List<String>> ARBOL = arbol();

    private CategoriasIniciales() {
    }

    private static Map<String, List<String>> arbol() {
        Map<String, List<String>> arbol = new LinkedHashMap<>();
        arbol.put("Facturas", List.of("Alquiler", "Luz", "Agua", "Internet", "Teléfono"));
        arbol.put("Necesidades", List.of("Comida", "Transporte", "Salud"));
        arbol.put("Deseos", List.of("Restaurantes", "Ocio", "Ropa"));
        arbol.put("Ahorro", List.of("Fondo de emergencia", "Vacaciones"));
        return Collections.unmodifiableMap(arbol);
    }
}
