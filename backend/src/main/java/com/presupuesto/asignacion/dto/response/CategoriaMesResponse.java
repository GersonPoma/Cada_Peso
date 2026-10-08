package com.presupuesto.asignacion.dto.response;

import com.presupuesto.categoria.entity.Categoria;

/**
 * Una categoría en un mes, con sus cifras ya calculadas (milésimas). {@code esPagoTarjeta} y
 * {@code cuentaId} (la tarjeta, nulo si no es una categoría de pago) son campos aditivos.
 */
public record CategoriaMesResponse(
        Long categoriaId,
        String nombre,
        boolean oculta,
        long asignado,
        long actividad,
        long disponible,
        boolean sobregastada,
        boolean esPagoTarjeta,
        Long cuentaId) {

    /** Una categoría normal (sin tarjeta): conserva el contrato anterior de siete campos. */
    public CategoriaMesResponse(
            Long categoriaId,
            String nombre,
            boolean oculta,
            long asignado,
            long actividad,
            long disponible,
            boolean sobregastada) {
        this(categoriaId, nombre, oculta, asignado, actividad, disponible, sobregastada, false,
                null);
    }

    public static CategoriaMesResponse desde(
            Categoria categoria, long asignado, long actividad, long disponible) {
        return new CategoriaMesResponse(
                categoria.getId(),
                categoria.getNombre(),
                categoria.isOculta(),
                asignado,
                actividad,
                disponible,
                disponible < 0,
                categoria.esPagoTarjeta(),
                categoria.esPagoTarjeta() ? categoria.getCuentaTarjeta().getId() : null);
    }
}
