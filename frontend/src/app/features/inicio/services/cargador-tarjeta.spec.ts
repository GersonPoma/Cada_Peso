import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { crearCargador } from './cargador-tarjeta';

describe('crearCargador', () => {
  let presupuesto: ReturnType<typeof signal<number | null>>;
  let pedidos: { id: number; salida: Subject<string> }[];
  let cancelados: number;

  function montar() {
    return TestBed.runInInjectionContext(() =>
      crearCargador(presupuesto, (id) => {
        const salida = new Subject<string>();
        pedidos.push({ id, salida });
        // Si el teardown corre sin que la fuente haya fallado, la petición se canceló.
        return new Observable<string>((observador) => {
          let fallo = false;
          const suscripcion = salida.subscribe({
            next: (valor) => observador.next(valor),
            error: (error) => {
              fallo = true;
              observador.error(error);
            },
          });
          return () => {
            if (!fallo) {
              cancelados++;
            }
            suscripcion.unsubscribe();
          };
        });
      }),
    );
  }

  beforeEach(() => {
    presupuesto = signal<number | null>(null);
    pedidos = [];
    cancelados = 0;
  });

  it('sin presupuesto no pide nada y queda cargando', () => {
    const cargador = montar();
    TestBed.tick();

    expect(pedidos).toEqual([]);
    expect(cargador.estado()).toBe('cargando');
    expect(cargador.datos()).toBeNull();
  });

  it('con presupuesto pide su fuente y al responder queda listo con los datos', () => {
    presupuesto.set(3);
    const cargador = montar();
    TestBed.tick();

    expect(pedidos.map((p) => p.id)).toEqual([3]);
    expect(cargador.estado()).toBe('cargando');
    pedidos[0].salida.next('cifras');
    expect(cargador.estado()).toBe('listo');
    expect(cargador.datos()).toBe('cifras');
  });

  it('un error deja la tarjeta en error sin datos', () => {
    presupuesto.set(3);
    const cargador = montar();
    TestBed.tick();

    pedidos[0].salida.error(new Error('500'));

    expect(cargador.estado()).toBe('error');
    expect(cargador.datos()).toBeNull();
  });

  it('recargar repite solo su petición y vuelve a cargando', () => {
    presupuesto.set(3);
    const cargador = montar();
    TestBed.tick();
    pedidos[0].salida.error(new Error('500'));

    cargador.recargar();
    TestBed.tick();

    expect(pedidos.map((p) => p.id)).toEqual([3, 3]);
    expect(cargador.estado()).toBe('cargando');
    pedidos[1].salida.next('ok');
    expect(cargador.estado()).toBe('listo');
  });

  it('la respuesta de una petición reemplazada por un reintento se descarta', () => {
    presupuesto.set(3);
    const cargador = montar();
    TestBed.tick();

    cargador.recargar();
    TestBed.tick();
    pedidos[0].salida.next('vieja');
    expect(cargador.datos()).toBeNull();
    pedidos[1].salida.next('nueva');

    expect(cargador.datos()).toBe('nueva');
    expect(cancelados).toBe(1);
  });

  it('al cambiar de presupuesto limpia los datos y descarta la respuesta del anterior', () => {
    presupuesto.set(3);
    const cargador = montar();
    TestBed.tick();
    pedidos[0].salida.next('del 3');
    expect(cargador.datos()).toBe('del 3');

    presupuesto.set(4);
    TestBed.tick();
    expect(cargador.datos()).toBeNull();
    expect(cargador.estado()).toBe('cargando');
    pedidos[0].salida.next('tarde del 3');
    expect(cargador.datos()).toBeNull();
    pedidos[1].salida.next('del 4');

    expect(pedidos.map((p) => p.id)).toEqual([3, 4]);
    expect(cargador.datos()).toBe('del 4');
  });

  it('al destruirse cancela la petición pendiente y no actualiza nada', () => {
    presupuesto.set(3);
    const cargador = montar();
    TestBed.tick();

    TestBed.resetTestingModule();
    pedidos[0].salida.next('tarde');

    expect(cargador.datos()).toBeNull();
    expect(cargador.estado()).toBe('cargando');
    expect(cancelados).toBe(1);
  });
});
