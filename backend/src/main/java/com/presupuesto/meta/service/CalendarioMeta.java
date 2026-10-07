package com.presupuesto.meta.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Lógica de calendario de las metas como funciones puras, sin Spring ni base de datos. */
final class CalendarioMeta {

    private CalendarioMeta() {}

    /** Cuántas veces cae {@code diaSemana} (1 lunes a 7 domingo) en el mes. */
    static int vencimientosSemanales(YearMonth mes, int diaSemana) {
        int cuenta = 0;
        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            if (mes.atDay(dia).getDayOfWeek().getValue() == diaSemana) {
                cuenta++;
            }
        }
        return cuenta;
    }

    /**
     * Cuántas fechas {@code inicio + k × intervalo} (k ≥ 0) caen en el mes. {@code primero} es
     * el primer vencimiento en o después del primer día del mes; si es posterior al último día
     * (antes de {@code inicio} o un intervalo que salta el mes) no hay ninguno.
     */
    static int vencimientosPersonalizados(YearMonth mes, LocalDate inicio, int intervalo) {
        LocalDate primerDia = mes.atDay(1);
        LocalDate ultimoDia = mes.atEndOfMonth();
        LocalDate primero = inicio;
        if (inicio.isBefore(primerDia)) {
            long dias = ChronoUnit.DAYS.between(inicio, primerDia);
            long saltos = divisionHaciaArriba(dias, intervalo);
            primero = inicio.plusDays(saltos * intervalo);
        }
        if (primero.isAfter(ultimoDia)) {
            return 0;
        }
        return (int) (ChronoUnit.DAYS.between(primero, ultimoDia) / intervalo) + 1;
    }

    /** Meses desde {@code mes} hasta el de la fecha objetivo, ambos incluidos; mínimo 1. */
    static int mesesRestantes(YearMonth mes, LocalDate fechaObjetivo) {
        long meses = ChronoUnit.MONTHS.between(mes, YearMonth.from(fechaObjetivo)) + 1;
        return (int) Math.max(1L, meses);
    }

    /** División entera redondeada hacia arriba; solo para numerador positivo. */
    static long divisionHaciaArriba(long numerador, long divisor) {
        return Math.ceilDiv(numerador, divisor);
    }
}
