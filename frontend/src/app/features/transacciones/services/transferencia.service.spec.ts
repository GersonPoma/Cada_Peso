import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TransferenciaService } from './transferencia.service';

const URL = '/api/v1/presupuestos/3/transferencias';

describe('TransferenciaService', () => {
  let servicio: TransferenciaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(TransferenciaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('crear hace POST con el cuerpo', () => {
    const solicitud = {
      cuentaOrigenId: 1,
      cuentaDestinoId: 2,
      fecha: '2026-10-08',
      monto: 150000,
      memo: null,
    };
    servicio.crear(3, solicitud).subscribe();
    const peticion = backend.expectOne(URL);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(solicitud);
    peticion.flush({});
  });

  it('obtener hace GET con el id de la pata', () => {
    servicio.obtener(3, 21).subscribe();
    const peticion = backend.expectOne(`${URL}/21`);

    expect(peticion.request.method).toBe('GET');
    peticion.flush({});
  });

  it('actualizar hace PUT con el cuerpo, sin cuentas', () => {
    const solicitud = { fecha: '2026-10-08', monto: 60000, categoriaId: 7, memo: 'Cuota' };
    servicio.actualizar(3, 21, solicitud).subscribe();
    const peticion = backend.expectOne(`${URL}/21`);

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(solicitud);
    peticion.flush({});
  });

  it('borrar hace DELETE con el id de la pata', () => {
    servicio.borrar(3, 20).subscribe();
    const peticion = backend.expectOne(`${URL}/20`);

    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });
  });
});
