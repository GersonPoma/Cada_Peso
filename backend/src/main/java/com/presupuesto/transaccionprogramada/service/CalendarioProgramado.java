package com.presupuesto.transaccionprogramada.service;

import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cálculo puro de las ocurrencias de una transacción programada. Siempre parte de
 * {@code fechaInicio} y nunca encadena (anterior + periodo): así un día 31 recupera el 31 en los
 * meses que lo tienen en vez de quedarse en 28. Si el día no existe en el mes, usa el último.
 */
final class CalendarioProgramado {

    private CalendarioProgramado() {}

    /** Ocurrencia número {@code n}, contando desde 0 (la 0 es {@code inicio}). */
    static LocalDate ocurrencia(LocalDate inicio, FrecuenciaProgramada frecuencia, long n) {
        if (frecuencia.esEnMeses()) {
            // plusMonths parte siempre de inicio y recorta al último día del mes.
            return inicio.plusMonths(n * frecuencia.getMeses());
        }
        return inicio.plusDays(n * frecuencia.getDias());
    }

    /** Primera ocurrencia igual o posterior a {@code fecha}. */
    static LocalDate primeraDesde(
            LocalDate inicio, FrecuenciaProgramada frecuencia, LocalDate fecha) {
        if (!fecha.isAfter(inicio)) {
            return inicio;
        }
        long n = Math.max(0, estimarIndice(inicio, frecuencia, fecha) - 1);
        LocalDate candidata = ocurrencia(inicio, frecuencia, n);
        while (candidata.isBefore(fecha)) {
            n++;
            candidata = ocurrencia(inicio, frecuencia, n);
        }
        return candidata;
    }

    /** Primera ocurrencia estrictamente posterior a {@code fecha}. */
    static LocalDate primeraDespuesDe(
            LocalDate inicio, FrecuenciaProgramada frecuencia, LocalDate fecha) {
        return primeraDesde(inicio, frecuencia, fecha.plusDays(1));
    }

    /** Índice aproximado, por defecto, para no recorrer ocurrencia a ocurrencia. */
    private static long estimarIndice(
            LocalDate inicio, FrecuenciaProgramada frecuencia, LocalDate fecha) {
        if (frecuencia.esEnMeses()) {
            return ChronoUnit.MONTHS.between(inicio, fecha) / frecuencia.getMeses();
        }
        return ChronoUnit.DAYS.between(inicio, fecha) / frecuencia.getDias();
    }
}
