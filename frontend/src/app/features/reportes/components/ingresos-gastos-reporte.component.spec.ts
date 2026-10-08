import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { IngresosGastosResponse } from '../models/ingresos-gastos-response.model';
import { MENSAJE_SIN_MOVIMIENTOS, NOTA_SALDO_INICIAL_INGRESOS } from '../services/mensajes-reporte';
import { IngresosGastosReporteComponent } from './ingresos-gastos-reporte.component';

const URL = '/api/v1/presupuestos/3/reportes/ingresos-gastos';

const DOS_MESES: IngresosGastosResponse = {
  desde: '2026-10',
  hasta: '2026-11',
  ingresos: 500000,
  gastos: 295000,
  neto: 205000,
  meses: [
    { mes: '2026-10', ingresos: 500000, gastos: 265000, neto: 235000 },
    { mes: '2026-11', ingresos: 0, gastos: 30000, neto: -30000 },
  ],
};

const normalizar = (texto: string | null | undefined) =>
  (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

describe('IngresosGastosReporteComponent', () => {
  let fixture: ComponentFixture<IngresosGastosReporteComponent>;
  let backend: HttpTestingController;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const filas = () =>
    Array.from(elemento().querySelectorAll('tr')).map((tr) =>
      Array.from(tr.querySelectorAll('th, td')).map((celda) => normalizar(celda.textContent)),
    );

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(IngresosGastosReporteComponent);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.componentRef.setInput('rango', { desde: '2026-10', hasta: '2026-11' });
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  function responder(datos: IngresosGastosResponse): void {
    const peticion = backend.expectOne((p) => p.url === URL);
    expect(peticion.request.params.get('desde')).toBe('2026-10');
    expect(peticion.request.params.get('hasta')).toBe('2026-11');
    peticion.flush(datos);
    fixture.detectChanges();
  }

  it('la tabla trae cada mes (también en ceros) y la fila Total de la API', () => {
    responder(DOS_MESES);

    const tabla = filas();
    expect(tabla[0]).toEqual(['Mes', 'Ingresos', 'Gastos', 'Neto']);
    expect(tabla).toHaveLength(4);
    expect(tabla[1].slice(1)).toEqual(['$500.00', '$265.00', '$235.00']);
    expect(tabla[2].slice(1, 3)).toEqual(['$0.00', '$300.00'.replace('300', '30')]);
    expect(tabla[3]).toEqual(['Total', '$500.00', '$295.00', '$205.00']);
  });

  it('un neto negativo dice Déficit además del color', () => {
    responder(DOS_MESES);

    expect(filas()[2][3]).toBe('-$30.00 Déficit');
    expect(filas()[1][3]).toBe('$235.00');
  });

  it('dibuja el gráfico con ingresos, gastos y la línea del neto, con su descripción', () => {
    responder(DOS_MESES);

    const svg = elemento().querySelector('svg[role="img"]') as SVGElement;
    expect(svg.getAttribute('aria-label')).toContain('Ingresos contra gastos por mes');
    expect(svg.querySelectorAll('rect.barra')).toHaveLength(4);
    expect(svg.querySelectorAll('circle.punto')).toHaveLength(2);
    expect(normalizar(elemento().textContent)).toContain(NOTA_SALDO_INICIAL_INGRESOS);
  });

  it('sin ingresos ni gastos muestra el aviso de vacío', () => {
    responder({
      ...DOS_MESES,
      ingresos: 0,
      gastos: 0,
      neto: 0,
      meses: DOS_MESES.meses.map((m) => ({ ...m, ingresos: 0, gastos: 0, neto: 0 })),
    });

    expect(normalizar(elemento().textContent)).toBe(MENSAJE_SIN_MOVIMIENTOS);
  });

  it('un error de red muestra el aviso genérico con Reintentar', () => {
    backend.expectOne((p) => p.url === URL).error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toBe(MENSAJE_ERROR_GENERICO);
    (elemento().querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    responder(DOS_MESES);
    expect(filas()).toHaveLength(4);
  });
});
