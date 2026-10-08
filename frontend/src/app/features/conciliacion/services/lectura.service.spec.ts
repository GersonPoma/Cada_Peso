import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CategoriaLecturaService } from './categoria-lectura.service';
import { CuentaLecturaService } from './cuenta-lectura.service';

describe('servicios de lectura de la conciliación', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('CuentaLecturaService pide la cuenta por id', () => {
    const banco = {
      id: 5,
      nombre: 'Banco',
      tipo: 'CORRIENTE',
      enPresupuesto: true,
      cerrada: false,
    };
    let cuenta: unknown;
    TestBed.inject(CuentaLecturaService)
      .obtener(3, 5)
      .subscribe((r) => (cuenta = r));
    const peticion = backend.expectOne('/api/v1/presupuestos/3/cuentas/5');
    peticion.flush(banco);

    expect(peticion.request.method).toBe('GET');
    expect(cuenta).toEqual(banco);
  });

  it('CategoriaLecturaService pide el árbol con las ocultas', () => {
    TestBed.inject(CategoriaLecturaService).arbol(3).subscribe();
    const peticion = backend.expectOne(
      (p) =>
        p.url === '/api/v1/presupuestos/3/categorias' && p.params.get('incluirOcultas') === 'true',
    );

    expect(peticion.request.method).toBe('GET');
    peticion.flush([]);
  });
});
