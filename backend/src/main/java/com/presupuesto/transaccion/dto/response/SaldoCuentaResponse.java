package com.presupuesto.transaccion.dto.response;

import com.presupuesto.cuenta.entity.Cuenta;

/**
 * Saldos en milésimas: {@code saldo} con todas las transacciones y {@code saldoConciliado} solo
 * con las conciliadas y reconciliadas.
 */
public record SaldoCuentaResponse(Long cuentaId, long saldo, long saldoConciliado) {

    /** Suma el saldo inicial de la cuenta a las sumas de sus transacciones (0 si no hay). */
    public static SaldoCuentaResponse desde(Cuenta cuenta, long sumaTotal, long sumaConciliada) {
        return new SaldoCuentaResponse(
                cuenta.getId(),
                cuenta.getSaldoInicial() + sumaTotal,
                cuenta.getSaldoInicial() + sumaConciliada);
    }
}
