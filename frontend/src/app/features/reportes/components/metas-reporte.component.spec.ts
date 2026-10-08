import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  CumplimientoMetasResponse,
  MesMetaResponse,
  MetaCumplimientoResponse,
} from '../models/cumplimiento-metas-response.model';
import { MENSAJE_SIN_METAS, NOTA_HISTORIAL_METAS } from '../services/mensajes-reporte';
import { MetasReporteComponent } from './metas-reporte.component';

const URL = '/api/v1/presupuestos/3/reportes/metas';

function mes(cambios: Partial<MesMetaResponse>): MesMetaResponse {
  return {
    mes: '2026-10',
    necesidad: 100000,
    asignado: 80000,
    gastado: 60000,
    disponible: 20000,
    faltante: 20000,
    estado: 'FALTA',
    porcentaje: 8000,
    ...cambios,
  };
}

function meta(cambios: Partial<MetaCumplimientoResponse>): MetaCumplimientoResponse {
  return {
    categoriaId: 8,
    nombre: 'Comida',
    oculta: false,
    tipo: 'MONTO_MENSUAL',
    monto: 100000,
    necesidad: 200000,
    asignado: 180000,
    gastado: 120000,
    porcentaje: 9000,
    meses: [mes({ mes: '2026-09', estado: 'FALTA' }), mes({})],
    ...cambios,
  };
}

const normalizar = (texto: string | null | undefined) =>
  (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

describe('MetasReporteComponent', () => {
  let fixture: ComponentFixture<MetasReporteComponent>;
  let backend: HttpTestingController;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);
  const filasDe = (seccion: Element) =>
    Array.from(seccion.querySelectorAll('tr'))
      .slice(1)
      .map((tr) => Array.from(tr.querySelectorAll('td')).map((td) => normalizar(td.textContent)));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MetasReporteComponent);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.componentRef.setInput('rango', { desde: '2026-09', hasta: '2026-10' });
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.componentRef.setInput('mesLocal', '2026-10');
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  function responder(metas: MetaCumplimientoResponse[]): void {
    const peticion = backend.expectOne((p) => p.url === URL);
    expect(peticion.request.params.get('desde')).toBe('2026-09');
    expect(peticion.request.params.get('hasta')).toBe('2026-10');
    const cuerpo: CumplimientoMetasResponse = { desde: '2026-09', hasta: '2026-10', metas };
    peticion.flush(cuerpo);
    fixture.detectChanges();
  }

  it('muestra cada meta con sus totales y el porcentaje del rango', () => {
    responder([meta({}), meta({ categoriaId: 9, nombre: 'Viaje', oculta: true })]);

    const secciones = Array.from(elemento().querySelectorAll('section.meta'));
    expect(secciones.map((s) => normalizar(s.querySelector('h3')?.textContent))).toEqual([
      'Comida',
      'Viaje Oculta',
    ]);
    const totales = normalizar(secciones[0].querySelector('dl')?.textContent);
    expect(totales).toContain('$200.00');
    expect(totales).toContain('$180.00');
    expect(totales).toContain('90.00%');
    expect(texto()).toContain(NOTA_HISTORIAL_METAS);
  });

  it('una fila por mes con estado en texto: Faltaron en el mes pasado y Falta en el actual', () => {
    responder([meta({})]);

    const filas = filasDe(elemento().querySelector('section.meta') as Element);
    expect(filas).toHaveLength(2);
    expect(filas[1].slice(1)).toEqual([
      '$100.00',
      '$80.00',
      '$60.00',
      '$20.00',
      '$20.00',
      'schedule Falta',
      '80.00%',
    ]);
    expect(filas[0][6]).toBe('history Faltaron');
    const estados = elemento().querySelectorAll('.estado');
    expect(estados[0].classList).toContain('tono-neutro');
    expect(estados[1].classList).toContain('tono-falta');
  });

  it('un porcentaje null se lee Sin necesidad', () => {
    responder([
      meta({
        porcentaje: null,
        meses: [mes({ estado: 'POSPUESTA', necesidad: 0, porcentaje: null })],
      }),
    ]);

    expect(texto()).toContain('Pospuesta');
    expect(texto()).toContain('Sin necesidad');
  });

  it('un porcentaje mayor que 100 % llena la barra y lo marca', () => {
    responder([meta({ porcentaje: 12500 })]);

    expect(texto()).toContain('125.00% Más de lo necesario');
    expect((elemento().querySelector('.barra') as HTMLElement).style.width).toBe('100%');
  });

  it('sin metas lo dice', () => {
    responder([]);

    expect(texto()).toBe(MENSAJE_SIN_METAS);
  });
});
