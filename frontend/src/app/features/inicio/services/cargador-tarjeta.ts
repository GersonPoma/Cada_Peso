import { Signal, computed, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Observable, catchError, filter, map, of, switchMap, tap } from 'rxjs';

export type EstadoTarjeta = 'cargando' | 'listo' | 'error';

/** Lo que una tarjeta necesita de su fuente: el estado, los datos y cómo reintentar. */
export interface CargadorTarjeta<T> {
  readonly estado: Signal<EstadoTarjeta>;
  /** Nulo mientras carga o si falló: nunca quedan datos de otro presupuesto. */
  readonly datos: Signal<T | null>;
  /** Repite solo la petición de esta fuente. */
  recargar(): void;
}

/**
 * Carga una fuente de una tarjeta de forma independiente: pide cuando hay presupuesto activo,
 * descarta la respuesta de una petición reemplazada (otro presupuesto o un reintento) con
 * `switchMap` y cancela al destruirse el contexto. Un error solo cambia el estado de esta fuente.
 * Debe llamarse en un contexto de inyección (constructor o inicializador de campo).
 */
export function crearCargador<T>(
  presupuestoId: Signal<number | null>,
  peticion: (presupuestoId: number) => Observable<T>,
): CargadorTarjeta<T> {
  const recargas = signal(0);
  const estado = signal<EstadoTarjeta>('cargando');
  const datos = signal<T | null>(null);

  toObservable(computed(() => ({ id: presupuestoId(), recargas: recargas() })))
    .pipe(
      filter(({ id }) => id !== null),
      tap(() => {
        estado.set('cargando');
        datos.set(null);
      }),
      switchMap(({ id }) =>
        peticion(id as number).pipe(
          map((respuesta) => ({ respuesta, error: false })),
          catchError(() => of({ respuesta: null, error: true })),
        ),
      ),
      takeUntilDestroyed(),
    )
    .subscribe(({ respuesta, error }) => {
      datos.set(respuesta);
      estado.set(error ? 'error' : 'listo');
    });

  return {
    estado: estado.asReadonly(),
    datos: datos.asReadonly(),
    recargar: () => recargas.update((n) => n + 1),
  };
}
