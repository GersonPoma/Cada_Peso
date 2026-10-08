package com.presupuesto.reporte.service;

import java.util.List;

/**
 * Cifras de cumplimiento de una meta, como funciones puras (milésimas). El porcentaje es
 * {@code asignado / necesidad} en centésimas de punto porcentual, sin tope (más de {@code 10000}
 * si se asignó de más) y {@code null} cuando la necesidad es 0 (meta pospuesta o ya cubierta):
 * no hay nada que cumplir. El total de un rango usa las sumas del rango, no el promedio de los
 * porcentajes mensuales.
 */
final class CalculoCumplimiento {

    /** Necesidad, asignado y gastado de una meta en un mes. */
    record Mes(long necesidad, long asignado, long gastado) {}

    /** Las mismas cifras sumadas en un rango y su porcentaje. */
    record Total(long necesidad, long asignado, long gastado, Long porcentaje) {}

    private CalculoCumplimiento() {}

    /** {@code null} si la necesidad no es positiva. */
    static Long porcentaje(long asignado, long necesidad) {
        return necesidad > 0 ? Porcentaje.centesimas(asignado, necesidad) : null;
    }

    /**
     * Lo gastado en el mes: el negativo de la actividad. En una categoría de pago de tarjeta la
     * actividad es una reserva, no un gasto, y vale 0.
     */
    static long gastado(long actividad, boolean categoriaDePagoDeTarjeta) {
        return categoriaDePagoDeTarjeta ? 0L : -actividad;
    }

    static Total totales(List<Mes> meses) {
        long necesidad = meses.stream().mapToLong(Mes::necesidad).sum();
        long asignado = meses.stream().mapToLong(Mes::asignado).sum();
        long gastado = meses.stream().mapToLong(Mes::gastado).sum();
        return new Total(necesidad, asignado, gastado, porcentaje(asignado, necesidad));
    }
}
