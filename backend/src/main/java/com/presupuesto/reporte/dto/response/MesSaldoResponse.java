package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;

/**
 * Movimiento y saldo de una cuenta en un mes (milésimas): suma de entradas, suma de salidas (con
 * signo) y saldo al cierre del mes.
 */
public record MesSaldoResponse(String mes, long entradas, long salidas, long saldo) {

    public static MesSaldoResponse desde(YearMonth mes, long entradas, long salidas, long saldo) {
        return new MesSaldoResponse(mes.toString(), entradas, salidas, saldo);
    }
}
