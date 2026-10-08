import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BeneficiarioResponse } from '../models/beneficiario-response.model';
import { BeneficiarioService } from './beneficiario.service';

const URL = '/api/v1/presupuestos/3/beneficiarios';
const NETFLIX: BeneficiarioResponse = { id: 4, nombre: 'Netflix', categoriaPredeterminadaId: 7 };

describe('BeneficiarioService', () => {
  let servicio: BeneficiarioService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(BeneficiarioService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('listar pide todos sin parámetros', () => {
    let lista: BeneficiarioResponse[] | undefined;
    servicio.listar(3).subscribe((r) => (lista = r));
    const peticion = backend.expectOne(URL);
    peticion.flush([NETFLIX]);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    expect(lista).toEqual([NETFLIX]);
  });

  it('crear hace POST con el cuerpo', () => {
    servicio.crear(3, { nombre: 'Netflix', categoriaId: 7 }).subscribe();
    const peticion = backend.expectOne(URL);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ nombre: 'Netflix', categoriaId: 7 });
    peticion.flush(NETFLIX);
  });

  it('actualizar hace PUT al beneficiario con el cuerpo', () => {
    servicio.actualizar(3, 4, { nombre: 'Netflix', categoriaId: null }).subscribe();
    const peticion = backend.expectOne(`${URL}/4`);

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Netflix', categoriaId: null });
    peticion.flush(NETFLIX);
  });
});
