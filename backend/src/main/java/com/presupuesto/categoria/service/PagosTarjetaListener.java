package com.presupuesto.categoria.service;

import com.presupuesto.cuenta.evento.CuentaCerradaEvento;
import com.presupuesto.cuenta.evento.CuentaCreadaEvento;
import com.presupuesto.cuenta.evento.CuentaReabiertaEvento;
import com.presupuesto.cuenta.evento.CuentaRenombradaEvento;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Mantiene la categoría de pago de cada tarjeta de crédito del presupuesto al crear, renombrar,
 * cerrar y reabrir la cuenta. Son {@code @EventListener} síncronos: corren en la transacción de
 * quien publicó el evento, así que un fallo revierte la cuenta y la categoría juntas.
 */
@Component
@RequiredArgsConstructor
class PagosTarjetaListener {

    private final CategoriasPagoTarjeta categorias;

    @EventListener
    void alCrearseLaCuenta(CuentaCreadaEvento evento) {
        if (CategoriasPagoTarjeta.aplica(evento.cuenta())) {
            categorias.crear(evento.cuenta());
        }
    }

    @EventListener
    void alRenombrarseLaCuenta(CuentaRenombradaEvento evento) {
        if (CategoriasPagoTarjeta.aplica(evento.cuenta())) {
            categorias.renombrar(evento.cuenta());
        }
    }

    @EventListener
    void alCerrarseLaCuenta(CuentaCerradaEvento evento) {
        if (CategoriasPagoTarjeta.aplica(evento.cuenta())) {
            categorias.ocultar(evento.cuenta());
        }
    }

    @EventListener
    void alReabrirseLaCuenta(CuentaReabiertaEvento evento) {
        if (CategoriasPagoTarjeta.aplica(evento.cuenta())) {
            categorias.mostrar(evento.cuenta());
        }
    }
}
