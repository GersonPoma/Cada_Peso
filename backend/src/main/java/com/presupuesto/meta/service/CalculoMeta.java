package com.presupuesto.meta.service;

import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.meta.dto.response.EstadoMeta;
import com.presupuesto.meta.entity.Meta;
import java.time.YearMonth;

/**
 * Necesidad, faltante y estado de una meta en un mes, como función pura (milésimas). Con
 * {@code inicial = disponible - asignado - actividad} (el saldo positivo que llega del mes
 * anterior):
 * <pre>
 * MONTO_MENSUAL    necesidad = monto × vencimientos del mes
 * MONTO_PARA_FECHA necesidad = max(0, ceil((monto - inicial) / mesesRestantes))
 * SALDO_OBJETIVO   necesidad = max(0, monto - inicial)
 * faltante = max(0, necesidad - asignado); pospuesta: necesidad = faltante = 0
 * </pre>
 */
final class CalculoMeta {

    /** Resultado del cálculo de una meta en un mes. */
    record Resultado(long necesidad, long faltante, EstadoMeta estado) {}

    private CalculoMeta() {}

    static Resultado calcular(Meta meta, YearMonth mes, FilaMes fila, boolean pospuesta) {
        long necesidad = pospuesta ? 0L : necesidad(meta, mes, fila);
        long faltante = Math.max(0L, necesidad - fila.asignado());
        return new Resultado(necesidad, faltante, estado(fila, pospuesta, faltante));
    }

    private static long necesidad(Meta meta, YearMonth mes, FilaMes fila) {
        long inicial = fila.disponible() - fila.asignado() - fila.actividad();
        return switch (meta.getTipo()) {
            case MONTO_MENSUAL -> meta.getMonto() * vencimientos(meta, mes);
            case MONTO_PARA_FECHA -> {
                long porCubrir = meta.getMonto() - inicial;
                yield porCubrir <= 0
                        ? 0L
                        : CalendarioMeta.divisionHaciaArriba(
                                porCubrir,
                                CalendarioMeta.mesesRestantes(mes, meta.getFechaObjetivo()));
            }
            case SALDO_OBJETIVO -> Math.max(0L, meta.getMonto() - inicial);
        };
    }

    private static int vencimientos(Meta meta, YearMonth mes) {
        return switch (meta.getFrecuencia()) {
            case MENSUAL -> 1;
            case SEMANAL -> CalendarioMeta.vencimientosSemanales(mes, meta.getDiaSemana());
            case PERSONALIZADA -> CalendarioMeta.vencimientosPersonalizados(
                    mes, meta.getFechaInicio(), meta.getIntervaloDias());
        };
    }

    /** Prioridad: sobregastada, pospuesta, falta, financiada. */
    private static EstadoMeta estado(FilaMes fila, boolean pospuesta, long faltante) {
        if (fila.disponible() < 0) {
            return EstadoMeta.SOBREGASTADA;
        }
        if (pospuesta) {
            return EstadoMeta.POSPUESTA;
        }
        return faltante > 0 ? EstadoMeta.FALTA : EstadoMeta.FINANCIADA;
    }
}
