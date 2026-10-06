package com.presupuesto.cuenta.entity;

public enum TipoCuenta {
    CORRIENTE,
    AHORRO,
    EFECTIVO,
    TARJETA_CREDITO,
    INVERSION,
    PRESTAMO;

    /** Solo las cuentas de deuda pueden empezar con saldo negativo. */
    public boolean admiteSaldoNegativo() {
        return this == TARJETA_CREDITO || this == PRESTAMO;
    }
}
