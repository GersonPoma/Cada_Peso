import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CategoriaLecturaService } from './categoria-lectura.service';
import { CuentaLecturaService } from './cuenta-lectura.service';

describe('servicios de lectura', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('CuentaLecturaService pide todas las cuentas, cerradas incluidas', () => {
    let cuentas: unknown;
    TestBed.inject(CuentaLecturaService)
      .listar(3)
      .subscribe((r) => (cuentas = r));
    const peticion = backend.expectOne(
      (p) =>
        p.url === '/api/v1/presupuestos/3/cuentas' && p.params.get('incluirCerradas') === 'true',
    );
    peticion.flush([{ id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false }]);

    expect(peticion.request.method).toBe('GET');
    expect(cuentas).toEqual([{ id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false }]);
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
