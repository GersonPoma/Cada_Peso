import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ResumenListoParaAsignarComponent } from './resumen-listo-para-asignar.component';

function normalizar(texto: string): string {
  return texto.replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

describe('ResumenListoParaAsignarComponent', () => {
  let fixture: ComponentFixture<ResumenListoParaAsignarComponent>;

  const resumen = () => (fixture.nativeElement as HTMLElement).querySelector('.resumen');

  async function con(listo: number): Promise<void> {
    fixture.componentRef.setInput('listoParaAsignar', listo);
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    fixture = TestBed.createComponent(ResumenListoParaAsignarComponent);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('positivo: monto, "Listo para asignar" e ícono de visto', async () => {
    await con(500000);

    expect(resumen()?.classList.contains('positivo')).toBe(true);
    expect(resumen()?.querySelector('.icono')?.textContent).toBe('check_circle');
    expect(normalizar(resumen()?.querySelector('.monto')?.textContent ?? '')).toBe('$500.00');
    expect(resumen()?.querySelector('.etiqueta')?.textContent).toBe('Listo para asignar');
  });

  it('cero: "Todo asignado" sin monto', async () => {
    await con(0);

    expect(resumen()?.classList.contains('cero')).toBe(true);
    expect(resumen()?.querySelector('.icono')?.textContent).toBe('done_all');
    expect(resumen()?.querySelector('.monto')).toBeNull();
    expect(resumen()?.querySelector('.etiqueta')?.textContent).toBe('Todo asignado');
  });

  it('negativo: monto, "Asignaste de más" y estilo de error', async () => {
    await con(-20000);

    expect(resumen()?.classList.contains('negativo')).toBe(true);
    expect(resumen()?.classList.contains('resumen')).toBe(true);
    expect(resumen()?.querySelector('.icono')?.textContent).toBe('error');
    expect(normalizar(resumen()?.querySelector('.monto')?.textContent ?? '')).toBe('-$20.00');
    expect(resumen()?.querySelector('.etiqueta')?.textContent).toBe('Asignaste de más');
  });
});
