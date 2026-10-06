import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CabeceraComponent } from './cabecera.component';

@Component({
  imports: [CabeceraComponent],
  template: `<app-cabecera><button id="accion" type="button">Salir</button></app-cabecera>`,
})
class AnfitrionDePrueba {}

describe('CabeceraComponent', () => {
  function crear(): HTMLElement {
    const fixture = TestBed.createComponent(AnfitrionDePrueba);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra un mat-toolbar con el texto Cada Peso', () => {
    const barra = crear().querySelector('mat-toolbar');

    expect(barra).not.toBeNull();
    expect(barra?.textContent).toContain('Cada Peso');
  });

  it('muestra dentro de la barra el contenido proyectado', () => {
    const boton = crear().querySelector('mat-toolbar #accion');

    expect(boton?.textContent).toBe('Salir');
  });
});
