package com.presupuesto.reporte.service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gasto neto por categoría y por mes, como función pura (milésimas). El gasto de una categoría es
 * el negativo de su actividad del presupuesto mensual: un gasto de {@code -80000} suma
 * {@code 80000} y un reembolso de {@code +10000} resta {@code 10000} (el total de una categoría
 * puede quedar negativo).
 *
 * <p>No filtra categorías de pago de tarjeta: una categoría de pago no admite transacciones (422,
 * spec {@code transacciones}) y las consultas de actividad crudas no incluyen la reserva que
 * {@code CalculadoraMes} le suma, así que nunca llega aquí.
 */
final class CalculoGasto {

    /**
     * @param porCategoria gasto del rango por categoría; solo las categorías con algún movimiento
     *     en el rango (aunque su neto sea 0)
     * @param porMes gasto de todas las categorías por mes, con todos los meses del rango
     */
    record Gasto(Map<Long, Long> porCategoria, Map<YearMonth, Long> porMes) {}

    private CalculoGasto() {}

    /**
     * @param actividad actividad por categoría y mes (todos los meses hasta {@code hasta}); los
     *     meses anteriores a {@code desde} se descartan
     */
    static Gasto calcular(Map<Long, Map<YearMonth, Long>> actividad, RangoMeses rango) {
        Map<Long, Long> porCategoria = new LinkedHashMap<>();
        Map<YearMonth, Long> porMes = mesesEnCero(rango);
        actividad.forEach((categoriaId, porMesDeCategoria) ->
                porMesDeCategoria.forEach((mes, valor) -> {
                    if (porMes.containsKey(mes)) {
                        porCategoria.merge(categoriaId, -valor, Long::sum);
                        porMes.merge(mes, -valor, Long::sum);
                    }
                }));
        return new Gasto(porCategoria, porMes);
    }

    /**
     * Gasto "Sin categoría" por mes: el negativo de las salidas sin categoría (transacciones y
     * partes de divisiones), con todos los meses del rango.
     */
    static Map<YearMonth, Long> sinCategoria(
            Map<YearMonth, Long> salidasSimples,
            Map<YearMonth, Long> salidasDivididas,
            RangoMeses rango) {
        Map<YearMonth, Long> porMes = mesesEnCero(rango);
        for (Map<YearMonth, Long> salidas : List.of(salidasSimples, salidasDivididas)) {
            salidas.forEach((mes, valor) -> {
                if (porMes.containsKey(mes)) {
                    porMes.merge(mes, -valor, Long::sum);
                }
            });
        }
        return porMes;
    }

    /** Suma de los valores de un mapa por mes. */
    static long total(Map<YearMonth, Long> porMes) {
        return porMes.values().stream().mapToLong(Long::longValue).sum();
    }

    private static Map<YearMonth, Long> mesesEnCero(RangoMeses rango) {
        Map<YearMonth, Long> meses = new LinkedHashMap<>();
        rango.meses().forEach(mes -> meses.put(mes, 0L));
        return meses;
    }
}
