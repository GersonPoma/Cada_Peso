import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BarraMesComponent } from './barra-mes.component';

@Component({
  imports: [BarraMesComponent],
  template: `<app-barra-mes [mes]="mes()" (cambiar)="emitidos.push($event)" />`,
})
class AnfitrionDePrueba {
  readonly mes = signal('2026-10');
  readonly emitidos: string[] = [];
}

describe('BarraMesComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const boton = (etiqueta: string) =>
    elemento().querySelector(`button[aria-label="${etiqueta}"]`) as HTMLButtonElement;
  const hoy = () =>
    Array.from(elemento().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Hoy',
    ) as HTMLButtonElement;

  async function conMes(mes: string): Promise<void> {
    fixture.componentInstance.mes.set(mes);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('muestra el mes en texto largo según la región', () => {
    expect(elemento().querySelector('.mes')?.textContent?.trim()).toBe('octubre de 2026');
  });

  it('las flechas emiten el mes anterior y el siguiente, cruzando el año', async () => {
    await conMes('2026-12');
    boton('Mes siguiente').click();
    await conMes('2027-01');
    boton('Mes anterior').click();

    expect(fixture.componentInstance.emitidos).toEqual(['2027-01', '2026-12']);
  });

  it('Hoy emite el mes actual local', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 6, 12));

    hoy().click();

    expect(fixture.componentInstance.emitidos).toEqual(['2026-10']);
  });

  it('deshabilita Mes anterior en 2000-01 y Mes siguiente en 2100-12', async () => {
    await conMes('2000-01');
    expect(boton('Mes anterior').disabled).toBe(true);
    expect(boton('Mes siguiente').disabled).toBe(false);

    await conMes('2100-12');
    expect(boton('Mes anterior').disabled).toBe(false);
    expect(boton('Mes siguiente').disabled).toBe(true);
  });
});
