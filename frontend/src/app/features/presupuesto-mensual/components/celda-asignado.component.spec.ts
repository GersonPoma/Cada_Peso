import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { CeldaAsignadoComponent } from './celda-asignado.component';

@Component({
  imports: [CeldaAsignadoComponent],
  template: `
    <app-celda-asignado
      id="luz"
      nombre="Luz"
      moneda="USD"
      [asignado]="asignadoLuz()"
      [guardando]="guardando()"
      [errorServidor]="error()"
      (guardar)="guardados.push($event)"
    />
    <app-celda-asignado id="agua" nombre="Agua" moneda="USD" [asignado]="0" />
  `,
})
class AnfitrionDePrueba {
  readonly asignadoLuz = signal(100000);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly guardados: number[] = [];
}

describe('CeldaAsignadoComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const botonDe = (id: string) =>
    elemento().querySelector(`#${id} .boton-asignado`) as HTMLButtonElement | null;
  const campo = () => elemento().querySelector('#luz input') as HTMLInputElement | null;
  const guardados = () => fixture.componentInstance.guardados;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(): Promise<HTMLInputElement> {
    botonDe('luz')?.click();
    await estable();
    return campo() as HTMLInputElement;
  }

  async function escribir(texto: string): Promise<void> {
    const entrada = campo() as HTMLInputElement;
    entrada.value = texto;
    entrada.dispatchEvent(new Event('input'));
    await estable();
  }

  async function tecla(key: string, shiftKey = false): Promise<KeyboardEvent> {
    const evento = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
    campo()?.dispatchEvent(evento);
    await estable();
    return evento;
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({ providers: [...proveerMaterial()] });
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    await estable();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('muestra el monto como botón con una etiqueta accesible', () => {
    expect(botonDe('luz')?.getAttribute('aria-label')).toBe('Editar el asignado de Luz');
    expect(botonDe('luz')?.textContent).toContain('100');
  });

  it('al pulsarlo pasa a un campo con el valor en el formato de la región, enfocado y seleccionado', async () => {
    const entrada = await abrir();

    expect(entrada.value).toBe('100');
    expect(document.activeElement).toBe(entrada);
    expect(entrada.selectionStart).toBe(0);
    expect(entrada.selectionEnd).toBe(3);
  });

  it('Enter guarda en milésimas con el separador de la región y devuelve el foco al botón', async () => {
    await abrir();
    await escribir('150,5');
    const evento = await tecla('Enter');

    expect(evento.defaultPrevented).toBe(true);
    expect(guardados()).toEqual([150500]);
    expect(campo()).toBeNull();
    expect(document.activeElement).toBe(botonDe('luz'));
  });

  it('salir del campo guarda', async () => {
    await abrir();
    await escribir('20');
    campo()?.dispatchEvent(new Event('blur'));
    await estable();

    expect(guardados()).toEqual([20000]);
    expect(campo()).toBeNull();
  });

  it('Escape cancela sin emitir y devuelve el foco', async () => {
    await abrir();
    await escribir('999');
    await tecla('Escape');

    expect(guardados()).toEqual([]);
    expect(campo()).toBeNull();
    expect(document.activeElement).toBe(botonDe('luz'));
  });

  it('Tab guarda y lleva el foco a la celda siguiente', async () => {
    await abrir();
    await escribir('30');
    const evento = await tecla('Tab');

    expect(evento.defaultPrevented).toBe(true);
    expect(guardados()).toEqual([30000]);
    expect(document.activeElement).toBe(botonDe('agua'));
  });

  it('Shift+Tab no guarda por sí mismo: deja actuar al navegador', async () => {
    await abrir();
    const evento = await tecla('Tab', true);

    expect(evento.defaultPrevented).toBe(false);
  });

  it('vacío vale 0', async () => {
    await abrir();
    await escribir('   ');
    await tecla('Enter');

    expect(guardados()).toEqual([0]);
  });

  it('un texto inválido marca error, sigue editando y no emite', async () => {
    await abrir();
    await escribir('abc');
    await tecla('Enter');

    expect(guardados()).toEqual([]);
    expect(campo()).not.toBeNull();
    expect(elemento().querySelector('#luz mat-error')?.textContent).toContain('Monto inválido');

    await escribir('5');
    expect(elemento().querySelector('#luz mat-error')).toBeNull();
  });

  it('el mismo valor no emite', async () => {
    await abrir();
    await tecla('Enter');

    expect(guardados()).toEqual([]);
    expect(campo()).toBeNull();
  });

  it('con un guardado en curso no emite otro', async () => {
    fixture.componentInstance.guardando.set(true);
    await estable();
    await abrir();
    await escribir('7');
    await tecla('Enter');

    expect(guardados()).toEqual([]);
  });

  it('muestra el error del servidor debajo del monto', async () => {
    fixture.componentInstance.error.set('Monto fuera de rango');
    await estable();

    expect(elemento().querySelector('#luz [role="alert"]')?.textContent).toBe(
      'Monto fuera de rango',
    );
  });

  it('negativos con el separador de la región', async () => {
    await abrir();
    await escribir('-1.234,5');
    await tecla('Enter');

    expect(guardados()).toEqual([-1234500]);
  });
});
