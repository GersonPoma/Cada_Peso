package com.presupuesto.asignacion.service;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglas del presupuesto base cero por mes, como funciones puras (todo en milésimas):
 * <pre>
 * disponible(c, m) = max(0, disponible(c, m-1)) + asignado(c, m) + actividad(c, m)
 * listoParaAsignar(M) = ingresos - asignado(todas, meses hasta M)
 *                       - sobregastos al cierre de los meses anteriores a M
 * </pre>
 * El saldo positivo pasa al mes siguiente; el negativo (sobregasto) no se arrastra a la
 * categoría y reduce el listo para asignar de los meses siguientes. El cálculo es acumulado
 * desde el primer mes con datos hasta el mes pedido.
 */
public final class CalculoMensual {

    /** Cifras de una categoría en el mes pedido. */
    public record FilaMes(long asignado, long actividad, long disponible) {

        public static final FilaMes CERO = new FilaMes(0L, 0L, 0L);
    }

    /** Listo para asignar y la fila de cada categoría con datos (las demás valen CERO). */
    public record ResultadoMes(long listoParaAsignar, Map<Long, FilaMes> filas) {

        public FilaMes fila(Long categoriaId) {
            return filas.getOrDefault(categoriaId, FilaMes.CERO);
        }
    }

    private CalculoMensual() {}

    /**
     * @param asignado asignado por categoría y mes (solo meses hasta {@code mes})
     * @param actividad actividad por categoría y mes (solo meses hasta {@code mes})
     * @param ingresos ingresos acumulados hasta el fin de {@code mes}
     */
    static ResultadoMes calcular(
            YearMonth mes,
            Map<Long, Map<YearMonth, Long>> asignado,
            Map<Long, Map<YearMonth, Long>> actividad,
            long ingresos) {
        YearMonth primero = primerMes(mes, asignado, actividad);
        if (primero == null) {
            return new ResultadoMes(ingresos, Map.of());
        }
        Set<Long> categorias = new HashSet<>(asignado.keySet());
        categorias.addAll(actividad.keySet());
        Map<Long, FilaMes> filas = new HashMap<>();
        long totalAsignado = 0L;
        long sobregastos = 0L;
        for (Long categoria : categorias) {
            Map<YearMonth, Long> asignadoCat = asignado.getOrDefault(categoria, Map.of());
            Map<YearMonth, Long> actividadCat = actividad.getOrDefault(categoria, Map.of());
            long arrastre = 0L;
            for (YearMonth actual = primero; !actual.isAfter(mes); actual = actual.plusMonths(1)) {
                long asignadoMes = asignadoCat.getOrDefault(actual, 0L);
                long actividadMes = actividadCat.getOrDefault(actual, 0L);
                long disponible = Math.max(0L, arrastre) + asignadoMes + actividadMes;
                totalAsignado += asignadoMes;
                if (actual.equals(mes)) {
                    filas.put(categoria, new FilaMes(asignadoMes, actividadMes, disponible));
                } else if (disponible < 0) {
                    sobregastos += -disponible;
                }
                arrastre = disponible;
            }
        }
        return new ResultadoMes(ingresos - totalAsignado - sobregastos, filas);
    }

    /** El menor mes con datos que no pase de {@code mes}, o {@code null} si no hay ninguno. */
    private static YearMonth primerMes(
            YearMonth mes,
            Map<Long, Map<YearMonth, Long>> asignado,
            Map<Long, Map<YearMonth, Long>> actividad) {
        YearMonth primero = null;
        for (Map<Long, Map<YearMonth, Long>> datos : List.of(asignado, actividad)) {
            for (Map<YearMonth, Long> porMes : datos.values()) {
                for (YearMonth candidato : porMes.keySet()) {
                    boolean anterior = primero == null || candidato.isBefore(primero);
                    if (!candidato.isAfter(mes) && anterior) {
                        primero = candidato;
                    }
                }
            }
        }
        return primero;
    }
}
