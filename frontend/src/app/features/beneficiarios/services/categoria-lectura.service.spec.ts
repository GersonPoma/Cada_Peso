import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CategoriaLecturaService } from './categoria-lectura.service';

describe('CategoriaLecturaService (beneficiarios)', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('pide el árbol con las ocultas', () => {
    TestBed.inject(CategoriaLecturaService).arbol(3).subscribe();
    const peticion = backend.expectOne(
      (p) =>
        p.url === '/api/v1/presupuestos/3/categorias' && p.params.get('incluirOcultas') === 'true',
    );

    expect(peticion.request.method).toBe('GET');
    peticion.flush([]);
  });
});
