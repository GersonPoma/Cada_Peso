package com.presupuesto.conciliacion.service;

import com.presupuesto.cuenta.entity.TipoCuenta;

/** Reglas puras de la conciliación, sin base de datos. Importes en milésimas. */
final class CalculoConciliacion {

    private CalculoConciliacion() {
    }

    /** Lo que falta (positivo) o sobra (negativo) en lo conciliado para igualar el extracto. */
    static long diferencia(long saldoExtracto, long saldoConciliadoAlCorte) {
        return saldoExtracto - saldoConciliadoAlCorte;
    }

    /**
     * Si el ajuste de ese monto debe llevar categoría para que el dinero en cuentas siga siendo
     * {@code listoParaAsignar + disponibles}: en una cuenta del presupuesto, todo ajuste negativo
     * y el positivo de una tarjeta de crédito (que no cuenta como ingreso). Fuera del presupuesto
     * no cuenta para ningún cálculo.
     */
    static boolean exigeCategoria(boolean enPresupuesto, TipoCuenta tipo, long ajuste) {
        if (!enPresupuesto) {
            return false;
        }
        return ajuste < 0 || tipo == TipoCuenta.TARJETA_CREDITO;
    }
}
