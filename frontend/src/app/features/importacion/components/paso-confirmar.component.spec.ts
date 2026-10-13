import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { TotalesVistaPrevia } from '../models/vista-previa-response.model';
import { PasoConfirmarComponent } from './paso-confirmar.component';

const LIMPIOS: TotalesVistaPrevia = {
  total: 5,
  nuevas: 3,
  duplicadas: 2,
  invalidas: 0,
  sinCategoria: 3,
};

describe('PasoConfirmarComponent', () => {
  let fixture: ComponentFixture<PasoConfirmarComponent>;
  let enviados: boolean[];
  let volvio: number;

  function montar(totales: TotalesVistaPrevia, enviando = false, error: string | null = null) {
    fixture = TestBed.createComponent(PasoConfirmarComponent);
    fixture.componentRef.setInput('totales', totales);
    fixture.componentRef.setInput('enviando', enviando);
    fixture.componentRef.setInput('error', error);
    enviados = [];
    volvio = 0;
    fixture.componentInstance.importar.subscribe((v) => enviados.push(v));
    fixture.componentInstance.volver.subscribe(() => volvio++);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const importar = () => el().querySelector<HTMLButtonElement>('button.importar')!;
  const casilla = () => el().querySelector<HTMLInputElement>('input[type=checkbox]');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
  });

  it('sin inválidas no muestra la casilla y envía omitirInvalidas false', () => {
    montar(LIMPIOS);

    expect(casilla()).toBeNull();
    expect(importar().disabled).toBe(false);
    importar().click();
    expect(enviados).toEqual([false]);
  });

  it('con inválidas, Importar espera a que se marque Omitir las filas inválidas', () => {
    montar({ ...LIMPIOS, invalidas: 1, total: 6 });

    expect(el().textContent).toContain('Omitir las filas inválidas');
    expect(importar().disabled).toBe(true);
    expect(el().textContent).toContain('El archivo tiene filas inválidas');

    casilla()!.click();
    fixture.detectChanges();
    expect(importar().disabled).toBe(false);
    importar().click();
    expect(enviados).toEqual([true]);
  });

  it('sin filas nuevas no se puede importar y lo dice', () => {
    montar({ total: 2, nuevas: 0, duplicadas: 2, invalidas: 0, sinCategoria: 0 });

    expect(importar().disabled).toBe(true);
    expect(el().textContent).toContain('No hay filas nuevas para importar');
    importar().click();
    expect(enviados).toEqual([]);
  });

  it('al enviar deshabilita los botones y la casilla', () => {
    montar({ ...LIMPIOS, invalidas: 1, total: 6 }, true);

    expect(importar().disabled).toBe(true);
    expect(importar().textContent).toContain('Importando');
    expect(el().querySelector<HTMLButtonElement>('button.volver')!.disabled).toBe(true);
    expect(casilla()!.disabled).toBe(true);
  });

  it('muestra el error con role alert y Volver emite', () => {
    montar(LIMPIOS, false, 'No se pudo importar');

    expect(el().querySelector('[role=alert]')?.textContent).toContain('No se pudo importar');
    el().querySelector<HTMLButtonElement>('button.volver')!.click();
    expect(volvio).toBe(1);
  });
});
