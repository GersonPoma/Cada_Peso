import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CuentaImportacion } from '../models/cuenta-importacion.model';
import { CuentaLecturaService } from './cuenta-lectura.service';

describe('CuentaLecturaService', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('pide la cuenta por id con GET', () => {
    const banco: CuentaImportacion = {
      id: 5,
      nombre: 'Banco',
      tipo: 'CORRIENTE',
      enPresupuesto: true,
      cerrada: false,
    };
    let cuenta: CuentaImportacion | undefined;
    TestBed.inject(CuentaLecturaService)
      .obtener(3, 5)
      .subscribe((r) => (cuenta = r));

    const peticion = backend.expectOne('/api/v1/presupuestos/3/cuentas/5');
    peticion.flush(banco);

    expect(peticion.request.method).toBe('GET');
    expect(cuenta).toEqual(banco);
  });

  it('un 404 llega como error', () => {
    let status = 0;
    TestBed.inject(CuentaLecturaService)
      .obtener(3, 99)
      .subscribe({ error: (e: { status: number }) => (status = e.status) });

    backend
      .expectOne('/api/v1/presupuestos/3/cuentas/99')
      .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });

    expect(status).toBe(404);
  });
});
