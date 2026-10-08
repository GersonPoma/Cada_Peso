package com.presupuesto.reporte.service;

import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.reporte.service.CalculoSaldos.SaldoMes;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Activos, pasivos y patrimonio neto de cada mes, como función pura (milésimas). Las cuentas
 * corriente, de ahorro, de efectivo y de inversión suman a {@code activos}; las tarjetas de
 * crédito y los préstamos suman a {@code pasivos} con el signo cambiado (una deuda de
 * {@code -150000} es un pasivo de {@code 150000}). No se reclasifica por el signo del saldo, así
 * que {@code patrimonio = activos - pasivos} es siempre la suma de todos los saldos.
 */
final class CalculoPatrimonio {

    /** Los saldos mensuales de una cuenta y su tipo. */
    record CuentaConSaldos(TipoCuenta tipo, List<SaldoMes> saldos) {}

    /** Las tres cifras de un mes. */
    record PatrimonioMes(YearMonth mes, long activos, long pasivos, long patrimonio) {}

    private CalculoPatrimonio() {}

    static boolean esPasivo(TipoCuenta tipo) {
        return tipo == TipoCuenta.TARJETA_CREDITO || tipo == TipoCuenta.PRESTAMO;
    }

    /** Un elemento por cada mes del rango; sin cuentas todo vale 0. */
    static List<PatrimonioMes> calcular(List<CuentaConSaldos> cuentas, RangoMeses rango) {
        List<YearMonth> meses = rango.meses();
        List<PatrimonioMes> resultado = new ArrayList<>();
        for (int i = 0; i < meses.size(); i++) {
            long activos = 0L;
            long pasivos = 0L;
            for (CuentaConSaldos cuenta : cuentas) {
                long saldo = cuenta.saldos().get(i).saldo();
                if (esPasivo(cuenta.tipo())) {
                    pasivos -= saldo;
                } else {
                    activos += saldo;
                }
            }
            resultado.add(new PatrimonioMes(meses.get(i), activos, pasivos, activos - pasivos));
        }
        return resultado;
    }
}
