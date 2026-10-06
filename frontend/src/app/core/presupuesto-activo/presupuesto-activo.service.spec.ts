import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { PresupuestoActivoService } from './presupuesto-activo.service';

describe('PresupuestoActivoService', () => {
  let servicio: PresupuestoActivoService;

  beforeEach(() => {
    servicio = TestBed.inject(PresupuestoActivoService);
  });

  it('arranca sin presupuesto activo', () => {
    expect(servicio.presupuesto()).toBeNull();
  });

  it('fijar expone el id, el nombre y la moneda', () => {
    servicio.fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });

    expect(servicio.presupuesto()).toEqual({ id: 3, nombre: 'Casa', moneda: 'BOB' });
  });

  it('fijar otro presupuesto reemplaza al anterior', () => {
    servicio.fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });

    servicio.fijar({ id: 1, nombre: 'Viajes', moneda: 'USD' });

    expect(servicio.presupuesto()).toEqual({ id: 1, nombre: 'Viajes', moneda: 'USD' });
  });

  it('limpiar deja sin presupuesto activo', () => {
    servicio.fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });

    servicio.limpiar();

    expect(servicio.presupuesto()).toBeNull();
  });
});
