package com.presupuesto.categoria.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.evento.CuentaCerradaEvento;
import com.presupuesto.cuenta.evento.CuentaCreadaEvento;
import com.presupuesto.cuenta.evento.CuentaReabiertaEvento;
import com.presupuesto.cuenta.evento.CuentaRenombradaEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PagosTarjetaListenerTest {

    private CategoriasPagoTarjeta categorias;
    private PagosTarjetaListener listener;
    private Cuenta visa;
    private Cuenta seguimiento;
    private Cuenta corriente;

    @BeforeEach
    void preparar() {
        categorias = mock(CategoriasPagoTarjeta.class);
        listener = new PagosTarjetaListener(categorias);
        visa = cuenta(1L, TipoCuenta.TARJETA_CREDITO, true);
        seguimiento = cuenta(2L, TipoCuenta.TARJETA_CREDITO, false);
        corriente = cuenta(3L, TipoCuenta.CORRIENTE, true);
    }

    @Test
    void unaTarjetaDelPresupuestoActivaCadaOperacion() {
        listener.alCrearseLaCuenta(new CuentaCreadaEvento(visa));
        listener.alRenombrarseLaCuenta(new CuentaRenombradaEvento(visa));
        listener.alCerrarseLaCuenta(new CuentaCerradaEvento(visa));
        listener.alReabrirseLaCuenta(new CuentaReabiertaEvento(visa));

        verify(categorias).crear(visa);
        verify(categorias).renombrar(visa);
        verify(categorias).ocultar(visa);
        verify(categorias).mostrar(visa);
    }

    @Test
    void lasTarjetasDeSeguimientoYLasOtrasCuentasNoHacenNada() {
        for (Cuenta cuenta : new Cuenta[] {seguimiento, corriente}) {
            listener.alCrearseLaCuenta(new CuentaCreadaEvento(cuenta));
            listener.alRenombrarseLaCuenta(new CuentaRenombradaEvento(cuenta));
            listener.alCerrarseLaCuenta(new CuentaCerradaEvento(cuenta));
            listener.alReabrirseLaCuenta(new CuentaReabiertaEvento(cuenta));
        }

        verify(categorias, never()).crear(any());
        verify(categorias, never()).renombrar(any());
        verify(categorias, never()).ocultar(any());
        verify(categorias, never()).mostrar(any());
    }

    private static Cuenta cuenta(long id, TipoCuenta tipo, boolean enPresupuesto) {
        return Cuenta.builder().id(id).tipo(tipo).enPresupuesto(enPresupuesto).build();
    }
}
