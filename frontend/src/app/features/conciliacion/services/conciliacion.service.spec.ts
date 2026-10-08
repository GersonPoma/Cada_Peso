import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ConciliacionResponse } from '../models/conciliacion-response.model';
import { ConciliacionService } from './conciliacion.service';

const URL = '/api/v1/presupuestos/3/cuentas/5/conciliacion';

const CONCILIACION: ConciliacionResponse = {
  id: 1,
  cuentaId: 5,
  fecha: '2026-09-30',
  saldoExtracto: 150000,
  ajuste: -5000,
  transaccionAjusteId: 40,
  cantidadReconciliadas: 4,
  fechaCreacion: '2026-10-01T12:00:00Z',
};

describe('ConciliacionService', () => {
  let servicio: ConciliacionService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ConciliacionService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('estado pide GET /estado con saldoExtracto y fecha', () => {
    let estado: unknown;
    servicio.estado(3, 5, -150000, '2026-09-30').subscribe((r) => (estado = r));
    const peticion = backend.expectOne((p) => p.url === `${URL}/estado`);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('saldoExtracto')).toBe('-150000');
    expect(peticion.request.params.get('fecha')).toBe('2026-09-30');
    peticion.flush({ cuentaId: 5, diferencia: 0 });
    expect(estado).toEqual({ cuentaId: 5, diferencia: 0 });
  });

  it('crear hace POST con el cuerpo tal cual', () => {
    let creada: unknown;
    const cuerpo = {
      saldoExtracto: 150000,
      fecha: '2026-09-30',
      crearAjuste: true,
      categoriaId: 7,
    };
    servicio.crear(3, 5, cuerpo).subscribe((r) => (creada = r));
    const peticion = backend.expectOne(URL);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(cuerpo);
    peticion.flush(CONCILIACION);
    expect(creada).toEqual(CONCILIACION);
  });

  it('historial pide GET sin parámetros', () => {
    let historial: unknown;
    servicio.historial(3, 5).subscribe((r) => (historial = r));
    const peticion = backend.expectOne(URL);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush([CONCILIACION]);
    expect(historial).toEqual([CONCILIACION]);
  });
});
