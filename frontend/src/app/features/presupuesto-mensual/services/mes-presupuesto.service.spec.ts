import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MesPresupuestoService } from './mes-presupuesto.service';

const URL_MES = '/api/v1/presupuestos/3/meses/2026-10';

describe('MesPresupuestoService', () => {
  let servicio: MesPresupuestoService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(MesPresupuestoService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([false, true])('obtener pide el mes con incluirOcultas=%s', (incluirOcultas) => {
    let respuesta: unknown;

    servicio.obtener(3, '2026-10', incluirOcultas).subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne(
      (p) => p.url === URL_MES && p.params.get('incluirOcultas') === String(incluirOcultas),
    );
    peticion.flush({ mes: '2026-10' });

    expect(peticion.request.method).toBe('GET');
    expect(respuesta).toEqual({ mes: '2026-10' });
  });

  it('asignar hace PUT /categorias/{id} con el asignado', () => {
    servicio.asignar(3, '2026-10', 7, { asignado: 150500 }).subscribe();
    const peticion = backend.expectOne(`${URL_MES}/categorias/7`);

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ asignado: 150500 });
    peticion.flush({});
  });

  it('moverDinero hace POST /mover-dinero con origen, destino y monto', () => {
    servicio.moverDinero(3, '2026-10', { origenId: 5, destinoId: 6, monto: 30000 }).subscribe();
    const peticion = backend.expectOne(`${URL_MES}/mover-dinero`);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ origenId: 5, destinoId: 6, monto: 30000 });
    peticion.flush({});
  });
});
