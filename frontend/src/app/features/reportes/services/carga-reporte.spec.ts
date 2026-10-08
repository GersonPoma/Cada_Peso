import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, finalize } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { EstadoCarga } from '../models/estado-carga.model';
import { cargarReporte, mientrasActiva } from './carga-reporte';
import { MENSAJE_RANGO_INVALIDO } from './mensajes-reporte';

interface Parametros {
  desde: string;
  intento: number;
}

describe('cargarReporte', () => {
  let parametros: Subject<Parametros | null>;
  let respuestas: Map<string, Subject<string>>;
  let canceladas: string[];
  let emitidos: (EstadoCarga<string> | null)[];

  beforeEach(() => {
    parametros = new Subject();
    respuestas = new Map();
    canceladas = [];
    emitidos = [];
    parametros
      .pipe(
        cargarReporte((p: Parametros) => {
          const respuesta = new Subject<string>();
          respuestas.set(p.desde, respuesta);
          return respuesta.pipe(finalize(() => canceladas.push(p.desde)));
        }),
      )
      .subscribe((estado) => emitidos.push(estado));
  });

  it('emite cargando y luego listo', () => {
    parametros.next({ desde: '2026-01', intento: 0 });
    respuestas.get('2026-01')?.next('datos');

    expect(emitidos).toEqual([{ tipo: 'cargando' }, { tipo: 'listo', datos: 'datos' }]);
  });

  it('cancela la petición anterior y descarta su respuesta', () => {
    parametros.next({ desde: '2026-01', intento: 0 });
    parametros.next({ desde: '2026-04', intento: 0 });
    respuestas.get('2026-01')?.next('viejo');
    respuestas.get('2026-04')?.next('nuevo');

    expect(canceladas).toContain('2026-01');
    expect(emitidos.at(-1)).toEqual({ tipo: 'listo', datos: 'nuevo' });
    expect(emitidos).not.toContainEqual({ tipo: 'listo', datos: 'viejo' });
  });

  it('no repite la petición con los mismos parámetros', () => {
    parametros.next({ desde: '2026-01', intento: 0 });
    respuestas.get('2026-01')?.next('datos');
    respuestas.delete('2026-01');
    parametros.next({ desde: '2026-01', intento: 0 });

    expect(respuestas.has('2026-01')).toBe(false);
  });

  it('un intento nuevo repite la petición', () => {
    parametros.next({ desde: '2026-01', intento: 0 });
    respuestas.delete('2026-01');
    parametros.next({ desde: '2026-01', intento: 1 });

    expect(respuestas.has('2026-01')).toBe(true);
  });

  it('un error se convierte en el aviso por código', () => {
    parametros.next({ desde: '2026-01', intento: 0 });
    respuestas
      .get('2026-01')
      ?.error(new HttpErrorResponse({ status: 400, error: { codigo: 'DATOS_INVALIDOS' } }));

    expect(emitidos.at(-1)).toEqual({
      tipo: 'error',
      aviso: { mensaje: MENSAJE_RANGO_INVALIDO, accion: 'reintentar' },
    });
  });

  it('con parámetros null emite null sin pedir', () => {
    parametros.next(null);

    expect(emitidos).toEqual([null]);
    expect(respuestas.size).toBe(0);
  });
});

describe('mientrasActiva', () => {
  it('sigue los parámetros solo mientras la pestaña está activa', () => {
    TestBed.runInInjectionContext(() => {
      const activa = signal(false);
      const parametros = signal<string | null>('2026-01');
      const resultado = mientrasActiva(activa, parametros);

      expect(resultado()).toBeNull();
      activa.set(true);
      expect(resultado()).toBe('2026-01');
      activa.set(false);
      parametros.set('2026-04');
      expect(resultado()).toBe('2026-01');
      activa.set(true);
      expect(resultado()).toBe('2026-04');
    });
  });
});
