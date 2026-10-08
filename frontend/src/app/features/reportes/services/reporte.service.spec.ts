import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ReporteService } from './reporte.service';

const URL = '/api/v1/presupuestos/3/reportes';
const RANGO = { desde: '2026-01', hasta: '2026-06' };

describe('ReporteService', () => {
  let servicio: ReporteService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ReporteService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([
    ['gastoPorCategoria', 'gasto-por-categoria'],
    ['ingresosGastos', 'ingresos-gastos'],
    ['patrimonio', 'patrimonio'],
    ['metas', 'metas'],
  ] as const)('%s pide GET %s con desde y hasta', (metodo, ruta) => {
    let respuesta: unknown;
    (servicio[metodo](3, RANGO) as Observable<unknown>).subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne((p) => p.url === `${URL}/${ruta}`);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('desde')).toBe('2026-01');
    expect(peticion.request.params.get('hasta')).toBe('2026-06');
    peticion.flush({ desde: '2026-01' });
    expect(respuesta).toEqual({ desde: '2026-01' });
  });

  it('evolucionSaldo pide GET con la cuenta en la ruta', () => {
    servicio.evolucionSaldo(3, 5, RANGO).subscribe();
    const peticion = backend.expectOne((p) => p.url === `${URL}/cuentas/5/evolucion-saldo`);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('desde')).toBe('2026-01');
    expect(peticion.request.params.get('hasta')).toBe('2026-06');
    peticion.flush({});
  });
});
