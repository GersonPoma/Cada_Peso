import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CuentaLecturaService } from './cuenta-lectura.service';

describe('CuentaLecturaService (reportes)', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('listar pide todas las cuentas, también las cerradas', () => {
    let cuentas: unknown;
    TestBed.inject(CuentaLecturaService)
      .listar(3)
      .subscribe((r) => (cuentas = r));
    const peticion = backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/cuentas');

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('incluirCerradas')).toBe('true');
    peticion.flush([{ id: 5, nombre: 'Banco' }]);
    expect(cuentas).toEqual([{ id: 5, nombre: 'Banco' }]);
  });
});
