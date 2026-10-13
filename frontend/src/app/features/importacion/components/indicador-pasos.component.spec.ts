import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { IndicadorPasosComponent } from './indicador-pasos.component';

const PASOS = ['Archivo', 'Mapeo', 'Vista previa', 'Confirmar', 'Resultado'];

describe('IndicadorPasosComponent', () => {
  let fixture: ComponentFixture<IndicadorPasosComponent>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const items = () => Array.from(elemento().querySelectorAll('li'));

  function mostrar(actual: number): void {
    fixture.componentRef.setInput('pasos', PASOS);
    fixture.componentRef.setInput('actual', actual);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(IndicadorPasosComponent);
  });

  it('muestra los cinco pasos en una lista con nombre', () => {
    mostrar(0);

    expect(elemento().querySelector('ol')?.getAttribute('aria-label')).toBe(
      'Pasos de la importación',
    );
    expect(items().map((li) => li.querySelector('.nombre')?.textContent?.trim())).toEqual(PASOS);
  });

  it('solo el paso actual lleva aria-current="step"', () => {
    mostrar(1);

    expect(items().map((li) => li.getAttribute('aria-current'))).toEqual([
      null,
      'step',
      null,
      null,
      null,
    ]);
  });

  it('los pasos hechos llevan un ícono de verificación y el texto (hecho) para el lector', () => {
    mostrar(2);

    const hechos = items().slice(0, 2);
    expect(
      hechos.every((li) => li.querySelector('mat-icon')?.textContent?.trim() === 'check'),
    ).toBe(true);
    expect(
      hechos.every((li) => li.querySelector('.solo-lector')?.textContent?.trim() === '(hecho)'),
    ).toBe(true);
    expect(items()[2].querySelector('mat-icon')).toBeNull();
  });

  it('los pasos que faltan muestran su número', () => {
    mostrar(0);

    expect(items()[3].querySelector('.numero')?.textContent?.trim()).toBe('4');
  });
});
