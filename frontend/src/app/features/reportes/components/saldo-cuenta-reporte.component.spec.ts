import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CuentaReporte } from '../models/cuenta-reporte.model';
import { EvolucionSaldoResponse } from '../models/evolucion-saldo-response.model';
import {
  MENSAJE_CUENTA_INEXISTENTE,
  MENSAJE_ELEGIR_CUENTA,
  NOTA_SALDO_INICIAL,
} from '../services/mensajes-reporte';
import { SaldoCuentaReporteComponent } from './saldo-cuenta-reporte.component';

const URL_CUENTAS = '/api/v1/presupuestos/3/cuentas';
const url = (cuentaId: number) =>
  `/api/v1/presupuestos/3/reportes/cuentas/${cuentaId}/evolucion-saldo`;

const CUENTAS: CuentaReporte[] = [
  { id: 5, nombre: 'Banco', tipo: 'CORRIENTE', enPresupuesto: true, cerrada: false },
  { id: 6, nombre: 'Visa', tipo: 'TARJETA_CREDITO', enPresupuesto: true, cerrada: false },
  { id: 7, nombre: 'Inversión', tipo: 'INVERSION', enPresupuesto: false, cerrada: false },
  { id: 8, nombre: 'Ahorro viejo', tipo: 'AHORRO', enPresupuesto: true, cerrada: true },
];

function evolucion(cuenta: CuentaReporte): EvolucionSaldoResponse {
  return {
    cuentaId: cuenta.id,
    nombre: cuenta.nombre,
    tipo: cuenta.tipo,
    enPresupuesto: cuenta.enPresupuesto,
    cerrada: cuenta.cerrada,
    saldoInicial: 100000,
    desde: '2026-09',
    hasta: '2026-11',
    meses: [
      { mes: '2026-09', entradas: 50000, salidas: -20000, saldo: 130000 },
      { mes: '2026-10', entradas: 0, salidas: 0, saldo: 130000 },
      { mes: '2026-11', entradas: 0, salidas: -30000, saldo: 100000 },
    ],
  };
}

const normalizar = (texto: string | null | undefined) =>
  (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

describe('SaldoCuentaReporteComponent', { timeout: 30000 }, () => {
  let fixture: ComponentFixture<SaldoCuentaReporteComponent>;
  let backend: HttpTestingController;
  let cargador: HarnessLoader;
  let cambios: (number | null)[];

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);

  function crear(cuentaId: number | null): void {
    fixture = TestBed.createComponent(SaldoCuentaReporteComponent);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.componentRef.setInput('rango', { desde: '2026-09', hasta: '2026-11' });
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.componentRef.setInput('cuentaId', cuentaId);
    cambios = [];
    // Como la pantalla: la cuenta elegida vuelve como entrada.
    fixture.componentInstance.cuentaCambiada.subscribe((id) => {
      cambios.push(id);
      fixture.componentRef.setInput('cuentaId', id);
    });
    cargador = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
  }

  function responderCuentas(cuentas = CUENTAS): void {
    const peticion = backend.expectOne((p) => p.url === URL_CUENTAS);
    expect(peticion.request.params.get('incluirCerradas')).toBe('true');
    peticion.flush(cuentas);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('sin cuenta pide elegir una y no pide la evolución', () => {
    crear(null);
    responderCuentas();

    expect(texto()).toContain(MENSAJE_ELEGIR_CUENTA);
    expect(backend.match((p) => p.url.includes('evolucion-saldo'))).toHaveLength(0);
  });

  it('ofrece todas las cuentas con sus marcas', async () => {
    crear(null);
    responderCuentas();

    const select = await cargador.getHarness(MatSelectHarness);
    await select.open();
    const opciones = await Promise.all((await select.getOptions()).map((o) => o.getText()));
    expect(opciones.map((o) => normalizar(o))).toEqual([
      'Banco',
      'Visa',
      'Inversión · Fuera del presupuesto',
      'Ahorro viejo · Cerrada',
    ]);
  });

  it('elegir una cuenta la avisa y pide su evolución', async () => {
    crear(null);
    responderCuentas();

    await (await cargador.getHarness(MatSelectHarness)).clickOptions({ text: /Ahorro viejo/ });
    fixture.detectChanges();

    expect(cambios).toEqual([8]);
    const peticion = backend.expectOne((p) => p.url === url(8));
    expect(peticion.request.params.get('desde')).toBe('2026-09');
    peticion.flush(evolucion(CUENTAS[3]));
    fixture.detectChanges();
    const resumen = elemento().querySelector('.resumen') as HTMLElement;
    expect(normalizar(resumen.querySelector('.nombre')?.textContent)).toBe('Ahorro viejo');
    expect(normalizar(resumen.querySelector('.marca')?.textContent)).toBe('Cerrada');
  });

  it('muestra saldo inicial, tabla, gráfico, nota y enlace a transacciones', () => {
    crear(5);
    responderCuentas();
    backend.expectOne((p) => p.url === url(5)).flush(evolucion(CUENTAS[0]));
    fixture.detectChanges();

    expect(texto()).toContain('Saldo inicial: $100.00');
    expect(texto()).toContain(NOTA_SALDO_INICIAL);
    const filas = Array.from(elemento().querySelectorAll('tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => normalizar(td.textContent)),
    );
    expect(filas[3].slice(1)).toEqual(['$0.00', '-$300.00'.replace('300', '30'), '$100.00']);
    expect(elemento().querySelector('svg[role="img"]')?.getAttribute('aria-label')).toContain(
      'Saldo de Banco',
    );
    expect(elemento().querySelector('a.enlace')?.getAttribute('href')).toBe(
      '/presupuestos/3/transacciones?cuentaId=5&desde=2026-09-01&hasta=2026-11-30',
    );
  });

  it('un cuentaId que no está en la lista se quita', () => {
    crear(99);
    responderCuentas();

    expect(cambios).toEqual([null]);
    expect(texto()).toContain(MENSAJE_ELEGIR_CUENTA);
  });

  it('al cambiar de cuenta descarta la respuesta atrasada', () => {
    crear(5);
    responderCuentas();
    const vieja = backend.expectOne((p) => p.url === url(5));
    fixture.componentRef.setInput('cuentaId', 6);
    fixture.detectChanges();

    expect(vieja.cancelled).toBe(true);
    backend.expectOne((p) => p.url === url(6)).flush(evolucion(CUENTAS[1]));
    fixture.detectChanges();
    expect(texto()).toContain('Visa');
    expect(texto()).not.toContain('Saldo de Banco');
  });

  it('un 404 avisa que la cuenta ya no existe, recarga las cuentas y la quita', () => {
    crear(5);
    responderCuentas();
    backend
      .expectOne((p) => p.url === url(5))
      .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(cambios).toEqual([null]);
    responderCuentas(CUENTAS.slice(1));
    expect(elemento().querySelector('[role="alert"]')?.textContent).toBe(
      MENSAJE_CUENTA_INEXISTENTE,
    );
    expect(texto()).toContain(MENSAJE_ELEGIR_CUENTA);
  });
});
