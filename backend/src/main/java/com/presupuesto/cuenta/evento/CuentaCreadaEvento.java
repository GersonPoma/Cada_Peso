package com.presupuesto.cuenta.evento;

import com.presupuesto.cuenta.entity.Cuenta;

/**
 * Se publica, dentro de la transacción de la creación, cuando una cuenta nueva ya está
 * guardada. Otras
 * features lo escuchan con un {@code @EventListener} síncrono, así que {@code cuenta} no las
 * importa.
 */
public record CuentaCreadaEvento(Cuenta cuenta) {
}
