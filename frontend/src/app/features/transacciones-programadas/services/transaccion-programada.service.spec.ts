import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TransaccionProgramadaService } from './transaccion-programada.service';

const URL = '/api/v1/presupuestos/3/transacciones-programadas';

describe('TransaccionProgramadaService', () => {
  let servicio: TransaccionProgramadaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(TransaccionProgramadaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('listar pide GET sin filtros', () => {
    let lista: unknown;
    servicio.listar(3).subscribe((r) => (lista = r));
    const peticion = backend.expectOne(URL);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush([{ id: 1 }]);
    expect(lista).toEqual([{ id: 1 }]);
  });

  it('crear hace POST con el cuerpo tal cual', () => {
    const cuerpo = {
      cuentaId: 5,
      fechaInicio: '2026-11-05',
      frecuencia: 'MENSUAL' as const,
      fechaFin: null,
      monto: -1500000,
      categoriaId: 7,
      beneficiario: 'Inmobiliaria',
      memo: null,
    };
    servicio.crear(3, cuerpo).subscribe();
    const peticion = backend.expectOne(URL);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(cuerpo);
    peticion.flush({ id: 1 });
  });

  it('actualizar hace PUT sin la cuenta ni la fecha de inicio', () => {
    const cuerpo = {
      monto: -1600000,
      categoriaId: null,
      beneficiario: null,
      memo: null,
      frecuencia: 'SEMANAL' as const,
      fechaFin: '2026-12-31',
    };
    servicio.actualizar(3, 9, cuerpo).subscribe();
    const peticion = backend.expectOne(`${URL}/9`);

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(cuerpo);
    expect(peticion.request.body).not.toHaveProperty('cuentaId');
    expect(peticion.request.body).not.toHaveProperty('fechaInicio');
    peticion.flush({ id: 9 });
  });

  it('borrar hace DELETE', () => {
    servicio.borrar(3, 9).subscribe();
    const peticion = backend.expectOne(`${URL}/9`);

    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });
  });

  it.each(['pausar', 'reanudar'] as const)('%s hace POST a su ruta', (accion) => {
    servicio[accion](3, 9).subscribe();
    const peticion = backend.expectOne(`${URL}/9/${accion}`);

    expect(peticion.request.method).toBe('POST');
    peticion.flush({ id: 9 });
  });

  it('generar hace POST y devuelve las cantidades', () => {
    let resultado: unknown;
    servicio.generar(3).subscribe((r) => (resultado = r));
    const peticion = backend.expectOne(`${URL}/generar`);

    expect(peticion.request.method).toBe('POST');
    peticion.flush({ generadas: 2, plantillasConError: 0 });
    expect(resultado).toEqual({ generadas: 2, plantillasConError: 0 });
  });
});
