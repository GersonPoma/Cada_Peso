import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { ResumenInicioStore } from './resumen-inicio.store';

const BASE = '/api/v1/presupuestos/3';

describe('ResumenInicioStore', () => {
  let backend: HttpTestingController;
  let store: ResumenInicioStore;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 10, 0, 0));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ResumenInicioStore],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(ResumenInicioStore);
    TestBed.tick();
  });

  afterEach(() => {
    // Cada test valida las peticiones que le importan; las que siguen pendientes se descartan.
    backend.match(() => true);
    backend.verify();
    vi.useRealTimers();
  });

  it('el período es el mes actual con la fecha local', () => {
    expect(store.periodo).toBe('2026-10');
  });

  it('salen a la vez las seis peticiones del presupuesto, sin esperar ninguna respuesta', () => {
    const pendientes = backend.match(() => true).map((p) => p.request.urlWithParams);

    expect(pendientes.sort()).toEqual(
      [
        `${BASE}/meses/2026-10?incluirOcultas=true`,
        `${BASE}/cuentas`,
        `${BASE}/transacciones/saldos`,
        `${BASE}/meses/2026-10/metas`,
        `${BASE}/transacciones?soloSinAprobar=true&page=0&size=1`,
        `${BASE}/reportes/ingresos-gastos?desde=2026-10&hasta=2026-10`,
      ].sort(),
    );
    // Siguen pendientes: ninguna espera a otra.
    expect(store.mes.estado()).toBe('cargando');
    expect(store.saldo.estado()).toBe('cargando');
  });

  it('el mes alimenta dos tarjetas con una sola petición', () => {
    const delMes = backend.match((p) => p.url === `${BASE}/meses/2026-10`);

    expect(delMes).toHaveLength(1);
  });

  it('el fallo de una fuente no cambia el estado de las otras', () => {
    const peticiones = backend.match(() => true);
    const de = (url: string) => peticiones.find((p) => p.request.url === url)!;

    de(`${BASE}/meses/2026-10/metas`).flush({}, { status: 500, statusText: 'Server Error' });
    de(`${BASE}/meses/2026-10`).flush({ mes: '2026-10', listoParaAsignar: 5, grupos: [] });

    expect(store.metas.estado()).toBe('error');
    expect(store.mes.estado()).toBe('listo');
    expect(store.sinAprobar.estado()).toBe('cargando');
    expect(store.ingresosGastos.estado()).toBe('cargando');
  });

  it('el saldo falla si falla una de sus dos peticiones', () => {
    const peticiones = backend.match(() => true);
    const de = (url: string) => peticiones.find((p) => p.request.url === url)!;

    de(`${BASE}/cuentas`).flush([]);
    de(`${BASE}/transacciones/saldos`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(store.saldo.estado()).toBe('error');
  });

  it('reintentar una fuente repite solo su petición', () => {
    backend.match(() => true);

    store.metas.recargar();
    TestBed.tick();

    backend.expectOne(`${BASE}/meses/2026-10/metas`).flush({ mes: '2026-10', metas: [] });
    expect(store.metas.estado()).toBe('listo');
  });
});
