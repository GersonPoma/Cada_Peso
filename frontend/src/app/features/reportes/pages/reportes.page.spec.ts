import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { ReportesPage } from './reportes.page';

const BASE = '/api/v1/presupuestos/3/reportes';

const GASTO_VACIO = {
  desde: '2026-05',
  hasta: '2026-10',
  total: 0,
  grupos: [],
  sinCategoria: { total: 0, porcentaje: 0 },
};

describe('ReportesPage', { timeout: 30000 }, () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;

  const elemento = () => harness.routeNativeElement as HTMLElement;
  const url = () => TestBed.inject(Router).url;
  const pestanas = () => Array.from(elemento().querySelectorAll('[role="tab"]')) as HTMLElement[];
  const reportesPedidos = () =>
    backend.match((p) => p.url.startsWith(BASE)).map((p) => p.request.url.slice(BASE.length + 1));

  async function estable(): Promise<void> {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();
  }

  async function abrir(ruta: string): Promise<void> {
    await harness.navigateByUrl(ruta);
    await estable();
  }

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 8, 12));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'presupuestos/:presupuestoId/reportes', component: ReportesPage }]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'USD' });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('sin rango usa los últimos 6 meses, lo escribe en la URL y pide solo el gasto', async () => {
    await abrir('/presupuestos/3/reportes');

    expect(url()).toBe('/presupuestos/3/reportes?desde=2026-05&hasta=2026-10');
    const pedidas = backend.match((p) => p.url.startsWith(BASE));
    expect(pedidas.map((p) => p.request.url)).toEqual([`${BASE}/gasto-por-categoria`]);
    expect(pedidas[0].request.params.get('desde')).toBe('2026-05');
    expect(pedidas[0].request.params.get('hasta')).toBe('2026-10');
    expect(pestanas().map((p) => p.textContent?.trim())).toEqual([
      'Gasto',
      'Ingresos y gastos',
      'Patrimonio',
      'Saldo de una cuenta',
      'Metas',
    ]);
    expect(pestanas()[0].getAttribute('aria-selected')).toBe('true');
  });

  it('abre la pestaña y el rango de la URL', async () => {
    await abrir('/presupuestos/3/reportes?reporte=patrimonio&desde=2026-01&hasta=2026-06');

    const [peticion] = backend.match((p) => p.url.startsWith(BASE));
    expect(peticion.request.url).toBe(`${BASE}/patrimonio`);
    expect(peticion.request.params.get('desde')).toBe('2026-01');
    expect(pestanas()[2].getAttribute('aria-selected')).toBe('true');
    expect(url()).toBe('/presupuestos/3/reportes?reporte=patrimonio&desde=2026-01&hasta=2026-06');
  });

  it('una pestaña desconocida muestra Gasto', async () => {
    await abrir('/presupuestos/3/reportes?reporte=otro&desde=2026-01&hasta=2026-06');

    expect(reportesPedidos()).toEqual(['gasto-por-categoria']);
    expect(pestanas()[0].getAttribute('aria-selected')).toBe('true');
  });

  it('un rango mal formado se reemplaza sin pedir nada con él', async () => {
    await abrir('/presupuestos/3/reportes?desde=2026-13&hasta=2026-06');

    expect(url()).toBe('/presupuestos/3/reportes?desde=2026-05&hasta=2026-10');
    const pedidas = backend.match((p) => p.url.startsWith(BASE));
    expect(pedidas.every((p) => p.request.params.get('desde') === '2026-05')).toBe(true);
  });

  it('cambiar de pestaña escribe la URL y pide solo ese reporte, con el mismo rango', async () => {
    await abrir('/presupuestos/3/reportes?desde=2026-01&hasta=2026-03');
    backend.expectOne((p) => p.url === `${BASE}/gasto-por-categoria`).flush(GASTO_VACIO);
    await estable();

    pestanas()[1].click();
    await estable();

    expect(url()).toContain('reporte=ingresos-gastos');
    const [peticion] = backend.match((p) => p.url.startsWith(BASE));
    expect(peticion.request.url).toBe(`${BASE}/ingresos-gastos`);
    expect(peticion.request.params.get('desde')).toBe('2026-01');
    peticion.flush({
      desde: '2026-01',
      hasta: '2026-03',
      ingresos: 0,
      gastos: 0,
      neto: 0,
      meses: [],
    });
    await estable();

    // Volver a Gasto con el mismo rango no repite la petición.
    pestanas()[0].click();
    await estable();
    expect(reportesPedidos()).toEqual([]);
  });

  it('un atajo cambia el rango en la URL conservando la pestaña y la cuenta', async () => {
    await abrir('/presupuestos/3/reportes?reporte=metas&cuentaId=5&desde=2026-01&hasta=2026-03');
    backend.match((p) => p.url.startsWith(BASE));

    const atajo = Array.from(elemento().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Este año',
    ) as HTMLButtonElement;
    atajo.click();
    await estable();

    expect(url()).toBe(
      '/presupuestos/3/reportes?reporte=metas&cuentaId=5&desde=2026-01&hasta=2026-10',
    );
    const [peticion] = backend.match((p) => p.url === `${BASE}/metas`);
    expect(peticion.request.params.get('hasta')).toBe('2026-10');
  });
});
