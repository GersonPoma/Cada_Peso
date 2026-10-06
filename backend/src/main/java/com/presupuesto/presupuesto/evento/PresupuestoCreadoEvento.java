package com.presupuesto.presupuesto.evento;

import com.presupuesto.presupuesto.entity.Presupuesto;

/**
 * Se publica, dentro de la transacción de la creación, cuando un presupuesto nuevo ya está
 * guardado. Otras features lo escuchan con un {@code @EventListener} síncrono para preparar sus
 * datos iniciales; así {@code presupuesto} no las importa.
 */
public record PresupuestoCreadoEvento(Presupuesto presupuesto) {
}
