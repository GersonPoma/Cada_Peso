import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { MetaMesResponse } from '../models/metas-mes-response.model';
import { IndicadorMetaComponent } from './indicador-meta.component';

function meta(cambios: Partial<MetaMesResponse> = {}): MetaMesResponse {
  return {
    categoriaId: 7,
    nombre: 'Comida',
    tipo: 'MONTO_MENSUAL',
    monto: 100000,
    necesidad: 100000,
    asignado: 60000,
    disponible: 60000,
    faltante: 40000,
    estado: 'FALTA',
    ...cambios,
  };
}

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

describe('IndicadorMetaComponent', () => {
  let fixture: ComponentFixture<IndicadorMetaComponent>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const indicador = () => elemento().querySelector('.indicador') as HTMLElement;
  const estado = () => normalizar(elemento().querySelector('.estado')?.textContent);
  const barra = () => elemento().querySelector('mat-progress-bar') as HTMLElement;

  async function mostrar(m: MetaMesResponse, mesPasado = false): Promise<void> {
    fixture.componentRef.setInput('meta', m);
    fixture.componentRef.setInput('mesPasado', mesPasado);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(IndicadorMetaComponent);
    fixture.componentRef.setInput('moneda', 'USD');
  });

  afterEach(() => vi.unstubAllGlobals());

  it('con faltante muestra Falta, la meta del mes y la barra proporcional', async () => {
    await mostrar(meta());

    expect(estado()).toBe('schedule Falta $40.00');
    expect(normalizar(indicador().textContent)).toContain('Meta del mes: $100.00');
    expect(barra().getAttribute('aria-valuenow')).toBe('60');
    expect(barra().getAttribute('aria-label')).toBe(
      'Progreso de la meta de Comida: $60.00 asignado de $100.00',
    );
    expect(indicador().classList.contains('tono-falta')).toBe(true);
    expect(indicador().classList.contains('indicador')).toBe(true);
  });

  it('financiada dice Financiada con la barra llena', async () => {
    await mostrar(meta({ estado: 'FINANCIADA', asignado: 100000, faltante: 0 }));

    expect(estado()).toBe('check_circle Financiada');
    expect(barra().getAttribute('aria-valuenow')).toBe('100');
    expect(indicador().classList.contains('tono-ok')).toBe(true);
  });

  it('sobregastada dice Sobregastada con ícono de advertencia y tono de error', async () => {
    await mostrar(meta({ estado: 'SOBREGASTADA' }));

    expect(estado()).toBe('warning Sobregastada');
    expect(indicador().classList.contains('tono-error')).toBe(true);
  });

  it('pospuesta se ve con necesidad 0 y la barra llena, sin color de error', async () => {
    await mostrar(meta({ estado: 'POSPUESTA', necesidad: 0, asignado: 0, faltante: 0 }));

    expect(estado()).toBe('pause_circle Pospuesta este mes');
    expect(normalizar(indicador().textContent)).toContain('Meta del mes: $0.00');
    expect(barra().getAttribute('aria-valuenow')).toBe('100');
    expect(indicador().classList.contains('tono-neutro')).toBe(true);
  });

  it('en un mes pasado, Falta se ve como Faltaron en tono neutro', async () => {
    await mostrar(meta({ asignado: 0, faltante: 100000 }), true);

    expect(estado()).toBe('history Faltaron $100.00');
    expect(indicador().classList.contains('tono-neutro')).toBe(true);
    expect(indicador().classList.contains('tono-error')).toBe(false);
    expect(estado()).not.toContain('warning');
  });
});
