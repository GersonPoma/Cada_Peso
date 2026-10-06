import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CuentaResponse } from '../models/cuenta-response.model';
import { CuentaService } from './cuenta.service';

const URL_CUENTAS = '/api/v1/presupuestos/3/cuentas';

const BANCO: CuentaResponse = {
  id: 5,
  nombre: 'Banco',
  tipo: 'CORRIENTE',
  enPresupuesto: true,
  saldoInicial: 0,
  cerrada: false,
  fechaCreacion: '2026-10-06T12:00:00Z',
  fechaActualizacion: '2026-10-06T12:00:00Z',
};

describe('CuentaService', () => {
  let servicio: CuentaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CuentaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([false, true])('listar con incluirCerradas=%s', (incluirCerradas) => {
    let lista: CuentaResponse[] | undefined;

    servicio.listar(3, incluirCerradas).subscribe((respuesta) => (lista = respuesta));
    const peticion = backend.expectOne(
      (p) => p.url === URL_CUENTAS && p.params.get('incluirCerradas') === String(incluirCerradas),
    );
    peticion.flush([BANCO]);

    expect(peticion.request.method).toBe('GET');
    expect(lista).toEqual([BANCO]);
  });

  it('crear hace POST con el cuerpo', () => {
    const solicitud = {
      nombre: 'Banco',
      tipo: 'CORRIENTE' as const,
      enPresupuesto: true,
      saldoInicial: 1500,
    };

    servicio.crear(3, solicitud).subscribe();
    const peticion = backend.expectOne(URL_CUENTAS);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(solicitud);
    peticion.flush(BANCO);
  });

  it('actualizar hace PUT /cuentas/{id} con nombre y tipo', () => {
    servicio.actualizar(3, 5, { nombre: 'Ahorros', tipo: 'AHORRO' }).subscribe();
    const peticion = backend.expectOne(`${URL_CUENTAS}/5`);

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Ahorros', tipo: 'AHORRO' });
    peticion.flush(BANCO);
  });

  it('cerrar y reabrir hacen POST a su ruta', () => {
    servicio.cerrar(3, 5).subscribe();
    servicio.reabrir(3, 5).subscribe();

    const cerrar = backend.expectOne(`${URL_CUENTAS}/5/cerrar`);
    const reabrir = backend.expectOne(`${URL_CUENTAS}/5/reabrir`);
    expect(cerrar.request.method).toBe('POST');
    expect(reabrir.request.method).toBe('POST');
    cerrar.flush({ ...BANCO, cerrada: true });
    reabrir.flush(BANCO);
  });

  it('saldos hace GET /transacciones/saldos del presupuesto', () => {
    let saldos: unknown;

    servicio.saldos(3).subscribe((respuesta) => (saldos = respuesta));
    const peticion = backend.expectOne('/api/v1/presupuestos/3/transacciones/saldos');
    peticion.flush([{ cuentaId: 5, saldo: 1500, saldoConciliado: 0 }]);

    expect(peticion.request.method).toBe('GET');
    expect(saldos).toEqual([{ cuentaId: 5, saldo: 1500, saldoConciliado: 0 }]);
  });
});
