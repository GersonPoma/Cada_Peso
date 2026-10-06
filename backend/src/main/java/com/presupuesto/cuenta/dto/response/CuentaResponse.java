package com.presupuesto.cuenta.dto.response;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import java.time.Instant;

public record CuentaResponse(
        Long id,
        String nombre,
        TipoCuenta tipo,
        boolean enPresupuesto,
        long saldoInicial,
        boolean cerrada,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static CuentaResponse desde(Cuenta cuenta) {
        return new CuentaResponse(
                cuenta.getId(),
                cuenta.getNombre(),
                cuenta.getTipo(),
                cuenta.isEnPresupuesto(),
                cuenta.getSaldoInicial(),
                cuenta.isCerrada(),
                cuenta.getFechaCreacion(),
                cuenta.getFechaActualizacion());
    }
}
