import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { EstadoCarga } from '../models/estado-carga.model';
import { MENSAJE_SIN_MOVIMIENTOS } from '../services/mensajes-reporte';
import { EstadoReporteComponent } from './estado-reporte.component';

@Component({
  imports: [EstadoReporteComponent],
  template: `
    <app-estado-reporte
      [estado]="estado()"
      [vacio]="vacio()"
      (reintentar)="reintentos = reintentos + 1"
    >
      <p class="contenido">Contenido del reporte</p>
    </app-estado-reporte>
  `,
})
class Anfitrion {
  readonly estado = signal<EstadoCarga<unknown> | null>(null);
  readonly vacio = signal(false);
  reintentos = 0;
}

describe('EstadoReporteComponent', () => {
  let fixture: ComponentFixture<Anfitrion>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => elemento().textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const botones = () => Array.from(elemento().querySelectorAll('button'));

  function mostrar(estado: EstadoCarga<unknown> | null, vacio = false): void {
    fixture.componentInstance.estado.set(estado);
    fixture.componentInstance.vacio.set(vacio);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  });

  it('sin estado no muestra nada', () => {
    expect(texto()).toBe('');
  });

  it('mientras carga muestra el indicador', () => {
    mostrar({ tipo: 'cargando' });

    expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
    expect(texto()).not.toContain('Contenido del reporte');
  });

  it('con datos muestra el contenido', () => {
    mostrar({ tipo: 'listo', datos: {} });

    expect(texto()).toContain('Contenido del reporte');
  });

  it('con datos vacíos muestra el aviso de vacío', () => {
    mostrar({ tipo: 'listo', datos: {} }, true);

    expect(texto()).toBe(MENSAJE_SIN_MOVIMIENTOS);
    expect(elemento().querySelector('[role="status"]')).not.toBeNull();
  });

  it('un error se anuncia y Reintentar avisa', () => {
    mostrar({ tipo: 'error', aviso: { mensaje: 'Falló', accion: 'reintentar' } });

    expect(elemento().querySelector('[role="alert"]')?.textContent).toBe('Falló');
    botones()[0].click();
    expect(fixture.componentInstance.reintentos).toBe(1);
  });

  it('un presupuesto inexistente ofrece Recargar', () => {
    mostrar({ tipo: 'error', aviso: { mensaje: 'No existe', accion: 'recargar' } });

    expect(botones().map((b) => b.textContent?.trim())).toEqual(['Recargar']);
  });
});
