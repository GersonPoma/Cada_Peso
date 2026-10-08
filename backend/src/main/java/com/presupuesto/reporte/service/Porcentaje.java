package com.presupuesto.reporte.service;

import java.math.BigInteger;

/** Porcentajes en enteros, sin coma flotante. */
final class Porcentaje {

    private static final BigInteger CIEN_PORCIENTO = BigInteger.valueOf(10_000L);

    private Porcentaje() {}

    /**
     * {@code parte / total} en centésimas de punto porcentual ({@code 1234} = 12,34 %), al entero
     * más cercano con el medio alejándose de cero. Devuelve 0 si {@code total} no es positivo.
     */
    static long centesimas(long parte, long total) {
        if (total <= 0) {
            return 0L;
        }
        BigInteger numerador = BigInteger.valueOf(parte).multiply(CIEN_PORCIENTO);
        BigInteger divisor = BigInteger.valueOf(total);
        BigInteger magnitud = numerador.abs().shiftLeft(1).add(divisor)
                .divide(divisor.shiftLeft(1));
        BigInteger resultado = numerador.signum() < 0 ? magnitud.negate() : magnitud;
        return resultado.longValueExact();
    }
}
