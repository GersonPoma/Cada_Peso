package com.presupuesto.cuenta.evento;

import com.presupuesto.cuenta.entity.Cuenta;

/**
 * Se publica, dentro de la transacción, cuando una cuenta ya quedó reabierta. Otras
 * features lo escuchan con un {@code @EventListener} síncrono, así que {@code cuenta} no las
 * importa.
 */
public record CuentaReabiertaEvento(Cuenta cuenta) {
}
