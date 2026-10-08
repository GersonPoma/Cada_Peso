import { Signal, linkedSignal } from '@angular/core';
import {
  Observable,
  OperatorFunction,
  catchError,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { EstadoCarga } from '../models/estado-carga.model';
import { ContextoError, avisoError } from './errores-reporte';

/**
 * Los parámetros de un reporte solo mientras su pestaña está activa: en una pestaña oculta
 * conserva los últimos, así volver con el mismo rango no repite la petición y un cambio de rango
 * hecho en otra pestaña se pide recién al volver.
 */
export function mientrasActiva<P>(
  activa: Signal<boolean>,
  parametros: Signal<P | null>,
): Signal<P | null> {
  return linkedSignal<{ activa: boolean; parametros: P | null }, P | null>({
    source: () => ({ activa: activa(), parametros: parametros() }),
    computation: (fuente, previo) => (fuente.activa ? fuente.parametros : (previo?.value ?? null)),
  });
}

/**
 * Pide un reporte por cada parámetro distinto (comparado por valor): `switchMap` cancela la
 * petición anterior y descarta su respuesta. Emite `cargando` y luego `listo` o `error`; con
 * parámetros `null` (nada que pedir) emite `null`. Para reintentar, los parámetros llevan un
 * contador de intentos.
 */
export function cargarReporte<P, T>(
  peticion: (parametros: P) => Observable<T>,
  contexto: ContextoError = 'reporte',
): OperatorFunction<P | null, EstadoCarga<T> | null> {
  return (parametros$) =>
    parametros$.pipe(
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      switchMap((parametros) =>
        parametros === null
          ? of(null)
          : peticion(parametros).pipe(
              map((datos): EstadoCarga<T> => ({ tipo: 'listo', datos })),
              catchError((error: unknown) =>
                of<EstadoCarga<T>>({ tipo: 'error', aviso: avisoError(error, contexto) }),
              ),
              startWith<EstadoCarga<T>>({ tipo: 'cargando' }),
            ),
      ),
    );
}
