package com.presupuesto.reporte.dto.response;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import java.time.YearMonth;
import java.util.List;

/** Evolución mes a mes del saldo de una cuenta, partiendo de su {@code saldoInicial}. */
public record EvolucionSaldoResponse(
        Long cuentaId,
        String nombre,
        TipoCuenta tipo,
        boolean enPresupuesto,
        boolean cerrada,
        long saldoInicial,
        String desde,
        String hasta,
        List<MesSaldoResponse> meses) {

    public static EvolucionSaldoResponse desde(
            Cuenta cuenta, YearMonth desde, YearMonth hasta, List<MesSaldoResponse> meses) {
        return new EvolucionSaldoResponse(
                cuenta.getId(),
                cuenta.getNombre(),
                cuenta.getTipo(),
                cuenta.isEnPresupuesto(),
                cuenta.isCerrada(),
                cuenta.getSaldoInicial(),
                desde.toString(),
                hasta.toString(),
                meses);
    }
}
