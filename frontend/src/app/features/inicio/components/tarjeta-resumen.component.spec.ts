import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { EstadoTarjeta } from '../services/cargador-tarjeta';
import { TarjetaResumenComponent } from './tarjeta-resumen.component';

@Component({
  imports: [TarjetaResumenComponent],
  template: `
    <app-tarjeta-resumen
      titulo="Metas que faltan"
      clave="metas"
      [estado]="estado()"
      (reintentar)="reintentos = reintentos + 1"
    >
      <p class="contenido">3 metas por cumplir este mes</p>
    </app-tarjeta-resumen>
  `,
})
class Anfitrion {
  readonly estado = signal<EstadoTarjeta>('listo');
  reintentos = 0;
}

describe('TarjetaResumenComponent', () => {
  let fixture: ComponentFixture<Anfitrion>;

  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  });

  function poner(estado: EstadoTarjeta) {
    fixture.componentInstance.estado.set(estado);
    fixture.detectChanges();
  }

  it('es una región con el título como nombre', () => {
    const region = el().querySelector('section')!;
    const titulo = el().querySelector('h2')!;

    expect(region.getAttribute('aria-labelledby')).toBe(titulo.id);
    expect(titulo.id).toBe('titulo-tarjeta-metas');
    expect(titulo.textContent).toContain('Metas que faltan');
  });

  it('listo muestra el contenido proyectado', () => {
    expect(el().querySelector('.contenido')?.textContent).toContain('3 metas');
    expect(el().querySelector('mat-progress-spinner')).toBeNull();
    expect(el().querySelector('[role=alert]')).toBeNull();
  });

  it('cargando muestra el spinner con nombre y no el contenido', () => {
    poner('cargando');

    const spinner = el().querySelector('mat-progress-spinner')!;
    expect(spinner.getAttribute('aria-label')).toBe('Cargando Metas que faltan');
    expect(el().querySelector('.contenido')).toBeNull();
  });

  it('error se anuncia con role alert, con texto, y ofrece Reintentar', () => {
    poner('error');

    expect(el().querySelector('[role=alert]')?.textContent).toContain(
      'No pudimos cargar esta información.',
    );
    expect(el().querySelector('.contenido')).toBeNull();
    const boton = el().querySelector('button.reintentar') as HTMLButtonElement;
    expect(boton.textContent).toContain('Reintentar');
    boton.click();
    expect(fixture.componentInstance.reintentos).toBe(1);
  });

  it('el cuerpo se anuncia con aria-live', () => {
    expect(el().querySelector('.cuerpo')?.getAttribute('aria-live')).toBe('polite');
  });
});
