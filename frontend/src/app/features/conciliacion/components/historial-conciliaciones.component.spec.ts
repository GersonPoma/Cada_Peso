import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConciliacionResponse } from '../models/conciliacion-response.model';
import {
  EstadoHistorial,
  HistorialConciliacionesComponent,
} from './historial-conciliaciones.component';

function conciliacion(cambios: Partial<ConciliacionResponse>): ConciliacionResponse {
  return {
    id: 1,
    cuentaId: 5,
    fecha: '2026-09-30',
    saldoExtracto: 150000,
    ajuste: 0,
    transaccionAjusteId: null,
    cantidadReconciliadas: 0,
    fechaCreacion: '2026-10-01T12:00:00Z',
    ...cambios,
  };
}

const SEPTIEMBRE = conciliacion({
  id: 2,
  fecha: '2026-09-30',
  ajuste: -5000,
  transaccionAjusteId: 40,
  cantidadReconciliadas: 4,
});
const AGOSTO = conciliacion({ id: 1, fecha: '2026-08-31', saldoExtracto: 90000, ajuste: 0 });

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

describe('HistorialConciliacionesComponent', () => {
  let fixture: ComponentFixture<HistorialConciliacionesComponent>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const filas = () => Array.from(elemento().querySelectorAll('li.conciliacion'));
  const celda = (fila: Element, clase: string) =>
    normalizar(fila.querySelector(`.${clase}`)?.textContent);

  async function mostrar(estado: EstadoHistorial, conciliaciones: ConciliacionResponse[] = []) {
    fixture.componentRef.setInput('estado', estado);
    fixture.componentRef.setInput('conciliaciones', conciliaciones);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    fixture = TestBed.createComponent(HistorialConciliacionesComponent);
    fixture.componentRef.setInput('moneda', 'USD');
  });

  afterEach(() => vi.unstubAllGlobals());

  it('muestra las filas en el orden recibido con fecha, saldo, ajuste y cantidad', async () => {
    await mostrar('listo', [SEPTIEMBRE, AGOSTO]);

    const [primera, segunda] = filas();
    expect(filas()).toHaveLength(2);
    expect(celda(primera, 'fecha')).toBe('September 30, 2026');
    expect(celda(primera, 'saldo')).toBe('$150.00');
    expect(celda(primera, 'ajuste')).toBe('-$5.00');
    expect(celda(primera, 'cantidad')).toBe('4');
    expect(celda(segunda, 'fecha')).toBe('August 31, 2026');
    expect(celda(segunda, 'saldo')).toBe('$90.00');
  });

  it('un ajuste 0 se muestra como —', async () => {
    await mostrar('listo', [AGOSTO]);

    expect(celda(filas()[0], 'ajuste')).toBe('—');
  });

  it('sin conciliaciones dice que aún no se concilió', async () => {
    await mostrar('listo');

    expect(normalizar(elemento().textContent)).toContain('Aún no has conciliado esta cuenta');
    expect(filas()).toHaveLength(0);
  });

  it('mientras carga muestra un indicador', async () => {
    await mostrar('cargando');

    expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
    expect(filas()).toHaveLength(0);
  });

  it('con error muestra el mensaje y Reintentar emite', async () => {
    const reintentos = vi.fn();
    fixture.componentInstance.reintentar.subscribe(reintentos);
    await mostrar('error');

    expect(normalizar(elemento().textContent)).toContain('No pudimos cargar el historial.');
    const boton = Array.from(elemento().querySelectorAll('button')).find(
      (b) => normalizar(b.textContent) === 'Reintentar',
    );
    boton?.click();

    expect(reintentos).toHaveBeenCalledTimes(1);
  });
});
