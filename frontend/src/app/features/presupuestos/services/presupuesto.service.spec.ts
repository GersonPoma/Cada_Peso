import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { PresupuestoService } from './presupuesto.service';

const CASA: PresupuestoResponse = {
  id: 3,
  nombre: 'Casa',
  moneda: 'BOB',
  fechaCreacion: '2026-10-06T12:00:00Z',
  fechaActualizacion: '2026-10-06T12:00:00Z',
};

describe('PresupuestoService', () => {
  let servicio: PresupuestoService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(PresupuestoService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('listar hace GET /api/v1/presupuestos y devuelve la lista', () => {
    let lista: PresupuestoResponse[] | undefined;

    servicio.listar().subscribe((respuesta) => (lista = respuesta));
    const peticion = backend.expectOne('/api/v1/presupuestos');
    peticion.flush([CASA]);

    expect(peticion.request.method).toBe('GET');
    expect(lista).toEqual([CASA]);
  });

  it('crear hace POST con el nombre y la moneda', () => {
    servicio.crear({ nombre: 'Viajes', moneda: 'USD' }).subscribe();
    const peticion = backend.expectOne('/api/v1/presupuestos');

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ nombre: 'Viajes', moneda: 'USD' });
    peticion.flush(CASA);
  });

  it('crear sin moneda no la incluye en el cuerpo', () => {
    servicio.crear({ nombre: 'Casa' }).subscribe();
    const peticion = backend.expectOne('/api/v1/presupuestos');

    expect(peticion.request.body).toEqual({ nombre: 'Casa' });
    expect('moneda' in peticion.request.body).toBe(false);
    peticion.flush(CASA);
  });

  it('renombrar hace PUT /api/v1/presupuestos/{id} con el nombre', () => {
    let respuesta: PresupuestoResponse | undefined;

    servicio.renombrar(3, { nombre: 'Hogar' }).subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne('/api/v1/presupuestos/3');
    peticion.flush({ ...CASA, nombre: 'Hogar' });

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Hogar' });
    expect(respuesta?.nombre).toBe('Hogar');
  });
});
