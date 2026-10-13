import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ResumenLecturaService } from './resumen-lectura.service';

const BASE = '/api/v1/presupuestos/3';

describe('ResumenLecturaService', () => {
  let backend: HttpTestingController;
  let servicio: ResumenLecturaService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ResumenLecturaService);
  });

  afterEach(() => backend.verify());

  it('mes: GET del mes con incluirOcultas=true', () => {
    let recibido: unknown;
    servicio.mes(3, '2026-10').subscribe((r) => (recibido = r));

    const peticion = backend.expectOne(`${BASE}/meses/2026-10?incluirOcultas=true`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush({ mes: '2026-10', listoParaAsignar: 5, grupos: [] });

    expect(recibido).toEqual({ mes: '2026-10', listoParaAsignar: 5, grupos: [] });
  });

  it('cuentas: GET de las cuentas abiertas, sin incluirCerradas', () => {
    servicio.cuentas(3).subscribe();

    const peticion = backend.expectOne(`${BASE}/cuentas`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush([]);
  });

  it('saldos: GET de transacciones/saldos', () => {
    servicio.saldos(3).subscribe();

    const peticion = backend.expectOne(`${BASE}/transacciones/saldos`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush([]);
  });

  it('metas: GET de las metas del mes', () => {
    servicio.metas(3, '2026-10').subscribe();

    backend.expectOne(`${BASE}/meses/2026-10/metas`).flush({ mes: '2026-10', metas: [] });
  });

  it('sinAprobar: una página de una transacción con soloSinAprobar=true', () => {
    let total = -1;
    servicio.sinAprobar(3).subscribe((r) => (total = r.totalElementos));

    const peticion = backend.expectOne((r) => r.url === `${BASE}/transacciones`);
    expect(peticion.request.params.get('soloSinAprobar')).toBe('true');
    expect(peticion.request.params.get('page')).toBe('0');
    expect(peticion.request.params.get('size')).toBe('1');
    expect(peticion.request.params.keys().sort()).toEqual(['page', 'size', 'soloSinAprobar']);
    peticion.flush({ contenido: [], totalElementos: 7 });

    expect(total).toBe(7);
  });

  it('ingresosGastos: el reporte con desde y hasta del mismo mes', () => {
    servicio.ingresosGastos(3, '2026-10').subscribe();

    const peticion = backend.expectOne(
      `${BASE}/reportes/ingresos-gastos?desde=2026-10&hasta=2026-10`,
    );
    expect(peticion.request.method).toBe('GET');
    peticion.flush({ ingresos: 1, gastos: 2, neto: -1 });
  });
});
