import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CategoriaLecturaService } from './categoria-lectura.service';
import { CuentaLecturaService } from './cuenta-lectura.service';

describe('servicios de lectura (programadas)', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('CuentaLecturaService pide todas las cuentas, también las cerradas', () => {
    TestBed.inject(CuentaLecturaService).listar(3).subscribe();
    const peticion = backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/cuentas');

    expect(peticion.request.params.get('incluirCerradas')).toBe('true');
    peticion.flush([]);
  });

  it('CategoriaLecturaService pide el árbol con las ocultas', () => {
    TestBed.inject(CategoriaLecturaService).arbol(3).subscribe();
    const peticion = backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/categorias');

    expect(peticion.request.params.get('incluirOcultas')).toBe('true');
    peticion.flush([]);
  });
});
