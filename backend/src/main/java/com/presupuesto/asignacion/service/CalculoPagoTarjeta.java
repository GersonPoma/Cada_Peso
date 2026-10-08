package com.presupuesto.asignacion.service;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Regla pura de la reserva para pagar una tarjeta de crédito (todo en milésimas): la actividad
 * de la categoría de pago de una tarjeta en un mes es {@code -(suma de montos)} de los gastos con
 * categoría hechos con la tarjeta y de las patas de transferencia hacia ella que vienen de una
 * cuenta del presupuesto. Un gasto de {@code -30000} suma {@code +30000}, un reembolso resta y
 * un pago de {@code +30000} en la tarjeta resta {@code 30000}. El resultado se suma a la
 * actividad de {@link CalculoMensual}, que no cambia.
 */
final class CalculoPagoTarjeta {

    private CalculoPagoTarjeta() {}

    /**
     * @param sumas suma de montos por cuenta (tarjeta) y mes
     * @param categoriaDePago categoría de pago de cada tarjeta; una tarjeta sin categoría se
     *     ignora
     * @return la actividad de cada categoría de pago por mes
     */
    static Map<Long, Map<YearMonth, Long>> actividadPorCategoria(
            Map<Long, Map<YearMonth, Long>> sumas, Map<Long, Long> categoriaDePago) {
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        sumas.forEach((cuentaId, porMes) -> {
            Long categoriaId = categoriaDePago.get(cuentaId);
            if (categoriaId == null) {
                return;
            }
            Map<YearMonth, Long> destino =
                    actividad.computeIfAbsent(categoriaId, clave -> new HashMap<>());
            porMes.forEach((mes, suma) -> destino.merge(mes, -suma, Long::sum));
        });
        return actividad;
    }
}
