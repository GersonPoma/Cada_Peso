import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PresupuestoActivoService } from '../presupuesto-activo/presupuesto-activo.service';
import { CLAVE_SESION, SesionService } from './sesion.service';

describe('SesionService', () => {
  const AHORA = new Date('2026-10-06T12:00:00Z');

  /** Crea una instancia nueva: el servicio restaura la sesión al construirse. */
  function crearServicio(): SesionService {
    TestBed.resetTestingModule();
    return TestBed.inject(SesionService);
  }

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(AHORA);
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    localStorage.clear();
  });

  describe('al arrancar', () => {
    it('restaura una sesión cuyo token vence dentro de 2 horas', () => {
      localStorage.setItem(
        CLAVE_SESION,
        JSON.stringify({ token: 'abc', expiraEn: '2026-10-06T14:00:00Z' }),
      );

      const servicio = crearServicio();

      expect(servicio.haySesion()).toBe(true);
      expect(servicio.token()).toBe('abc');
    });

    it('arranca sin sesión y borra un token ya expirado', () => {
      localStorage.setItem(
        CLAVE_SESION,
        JSON.stringify({ token: 'abc', expiraEn: '2026-10-06T11:59:59Z' }),
      );

      const servicio = crearServicio();

      expect(servicio.haySesion()).toBe(false);
      expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    });

    it.each([
      ['un texto que no es JSON', 'esto no es json'],
      ['un JSON que no es un objeto', '42'],
      ['un JSON sin la expiración', JSON.stringify({ token: 'abc' })],
      ['un JSON con la expiración inválida', JSON.stringify({ token: 'abc', expiraEn: 'mañana' })],
    ])('arranca sin sesión y borra %s', (_descripcion, guardado) => {
      localStorage.setItem(CLAVE_SESION, guardado);

      const servicio = crearServicio();

      expect(servicio.haySesion()).toBe(false);
      expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    });

    it('arranca sin sesión si no hay nada guardado', () => {
      const servicio = crearServicio();

      expect(servicio.haySesion()).toBe(false);
      expect(servicio.token()).toBeNull();
    });
  });

  describe('iniciar y cerrar', () => {
    it('iniciar guarda el token y su expiración y activa la sesión', () => {
      const servicio = crearServicio();

      servicio.iniciar('abc', '2026-10-07T12:00:00Z');

      expect(servicio.haySesion()).toBe(true);
      expect(servicio.token()).toBe('abc');
      expect(servicio.sesion()).toEqual({ token: 'abc', expiraEn: '2026-10-07T12:00:00Z' });
      expect(JSON.parse(localStorage.getItem(CLAVE_SESION) ?? 'null')).toEqual({
        token: 'abc',
        expiraEn: '2026-10-07T12:00:00Z',
      });
    });

    it('una sesión iniciada se restaura en la siguiente carga', () => {
      crearServicio().iniciar('abc', '2026-10-07T12:00:00Z');

      expect(crearServicio().token()).toBe('abc');
    });

    it('cerrar borra la sesión y la clave del almacenamiento', () => {
      const servicio = crearServicio();
      servicio.iniciar('abc', '2026-10-07T12:00:00Z');

      servicio.cerrar();

      expect(servicio.haySesion()).toBe(false);
      expect(servicio.token()).toBeNull();
      expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    });

    it('cerrar deja sin presupuesto activo', () => {
      const servicio = crearServicio();
      const presupuestoActivo = TestBed.inject(PresupuestoActivoService);
      servicio.iniciar('abc', '2026-10-07T12:00:00Z');
      presupuestoActivo.fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });

      servicio.cerrar();

      expect(presupuestoActivo.presupuesto()).toBeNull();
    });
  });

  it('funciona en memoria, sin errores, si el almacenamiento lanza al leer y al escribir', () => {
    const bloqueado = () => {
      throw new Error('almacenamiento bloqueado');
    };
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(bloqueado);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(bloqueado);
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(bloqueado);

    const servicio = crearServicio();
    expect(servicio.haySesion()).toBe(false);

    servicio.iniciar('abc', '2026-10-07T12:00:00Z');
    expect(servicio.haySesion()).toBe(true);
    expect(servicio.token()).toBe('abc');

    servicio.cerrar();
    expect(servicio.haySesion()).toBe(false);
  });
});
