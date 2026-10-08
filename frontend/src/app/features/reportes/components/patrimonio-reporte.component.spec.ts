import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PatrimonioResponse } from '../models/patrimonio-response.model';
import {
  MENSAJE_PRESUPUESTO_INEXISTENTE,
  MENSAJE_SIN_MOVIMIENTOS,
  NOTA_PATRIMONIO,
  NOTA_SALDO_INICIAL,
} from '../services/mensajes-reporte';
import { PatrimonioReporteComponent } from './patrimonio-reporte.component';

const URL = '/api/v1/presupuestos/3/reportes/patrimonio';

const OCTUBRE: PatrimonioResponse = {
  desde: '2026-09',
  hasta: '2026-10',
  meses: [
    { mes: '2026-09', activos: 1800000, pasivos: 3050000, patrimonio: -1250000 },
    { mes: '2026-10', activos: 1800000, pasivos: 3050000, patrimonio: -1250000 },
  ],
};

const normalizar = (texto: string | null | undefined) =>
  (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

describe('PatrimonioReporteComponent', () => {
  let fixture: ComponentFixture<PatrimonioReporteComponent>;
  let backend: HttpTestingController;

  const elemento = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PatrimonioReporteComponent);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.componentRef.setInput('rango', { desde: '2026-09', hasta: '2026-10' });
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  function responder(datos: PatrimonioResponse): void {
    backend.expectOne((p) => p.url === URL).flush(datos);
    fixture.detectChanges();
  }

  it('la tabla muestra activos, pasivos y patrimonio negativo de cada mes', () => {
    responder(OCTUBRE);

    const celdas = Array.from(elemento().querySelectorAll('tr')[1].querySelectorAll('td')).map(
      (td) => normalizar(td.textContent),
    );
    expect(celdas.slice(1)).toEqual(['$1,800.00', '$3,050.00', '-$1,250.00']);
  });

  it('el gráfico tiene tres líneas, la del cero y su descripción', () => {
    responder(OCTUBRE);

    const svg = elemento().querySelector('svg[role="img"]') as SVGElement;
    expect(svg.querySelectorAll('g.serie')).toHaveLength(3);
    expect(svg.querySelector('line.cero')).not.toBeNull();
    expect(svg.getAttribute('aria-label')).toContain('Patrimonio, activos y pasivos');
  });

  it('explica qué cuentas entran y el saldo inicial', () => {
    responder(OCTUBRE);

    const texto = normalizar(elemento().textContent);
    expect(texto).toContain(NOTA_PATRIMONIO);
    expect(texto).toContain(NOTA_SALDO_INICIAL);
  });

  it('con todos los meses en cero muestra el aviso de vacío', () => {
    responder({
      ...OCTUBRE,
      meses: OCTUBRE.meses.map((m) => ({ ...m, activos: 0, pasivos: 0, patrimonio: 0 })),
    });

    expect(normalizar(elemento().textContent)).toBe(MENSAJE_SIN_MOVIMIENTOS);
  });

  it('un 404 avisa que el presupuesto no existe y ofrece Recargar', () => {
    backend
      .expectOne((p) => p.url === URL)
      .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toBe(
      MENSAJE_PRESUPUESTO_INEXISTENTE,
    );
    expect(normalizar(elemento().querySelector('button')?.textContent)).toBe('Recargar');
  });
});
