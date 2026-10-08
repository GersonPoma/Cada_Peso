package com.presupuesto.cuenta.evento;

import com.presupuesto.cuenta.entity.Cuenta;

/**
 * Se publica, dentro de la transacción de la edición, cuando una cuenta ya guardada cambió de
 * nombre. Otras
 * features lo escuchan con un {@code @EventListener} síncrono, así que {@code cuenta} no las
 * importa.
 */
public record CuentaRenombradaEvento(Cuenta cuenta) {
}
