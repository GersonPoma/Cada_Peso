package com.presupuesto.transaccion.service;

import com.presupuesto.beneficiario.entity.Beneficiario;
import java.time.LocalDate;

/**
 * Identifica un movimiento de una cuenta para detectar duplicados: misma fecha, mismo monto y
 * mismo beneficiario normalizado (sin beneficiario equivale a vacío).
 */
public record ClaveMovimiento(LocalDate fecha, long monto, String beneficiarioNormalizado) {

    public static ClaveMovimiento de(LocalDate fecha, long monto, String beneficiario) {
        return new ClaveMovimiento(
                fecha, monto, beneficiario == null ? "" : Beneficiario.normalizar(beneficiario));
    }
}
