import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MetaService } from './meta.service';

const URL_META = '/api/v1/presupuestos/3/categorias/7/meta';
const URL_MES = '/api/v1/presupuestos/3/meses/2026-10';

describe('MetaService', () => {
  let servicio: MetaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(MetaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('guardar hace PUT /categorias/{id}/meta con el cuerpo', () => {
    let respuesta: unknown;
    const cuerpo = { tipo: 'MONTO_MENSUAL' as const, monto: 100000, frecuencia: 'MENSUAL' as const };

    servicio.guardar(3, 7, cuerpo).subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne(URL_META);
    peticion.flush({ categoriaId: 7 });

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(cuerpo);
    expect(respuesta).toEqual({ categoriaId: 7 });
  });

  it('obtener hace GET /categorias/{id}/meta', () => {
    let respuesta: unknown;

    servicio.obtener(3, 7).subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne(URL_META);
    peticion.flush({ categoriaId: 7 });

    expect(peticion.request.method).toBe('GET');
    expect(respuesta).toEqual({ categoriaId: 7 });
  });

  it('quitar hace DELETE /categorias/{id}/meta', () => {
    servicio.quitar(3, 7).subscribe();
    const peticion = backend.expectOne(URL_META);

    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null);
  });

  it.each([false, true])('delMes pide las metas del mes con incluirOcultas=%s', (ocultas) => {
    let respuesta: unknown;

    servicio.delMes(3, '2026-10', ocultas).subscribe((r) => (respuesta = r));
    const peticion = backend.expectOne(
      (p) => p.url === `${URL_MES}/metas` && p.params.get('incluirOcultas') === String(ocultas),
    );
    peticion.flush({ mes: '2026-10', totalFaltante: 0, metas: [] });

    expect(peticion.request.method).toBe('GET');
    expect(respuesta).toEqual({ mes: '2026-10', totalFaltante: 0, metas: [] });
  });

  it.each(['posponer', 'reanudar'] as const)('%s hace POST sin cuerpo', (accion) => {
    servicio[accion](3, '2026-10', 7).subscribe();
    const peticion = backend.expectOne(`${URL_MES}/metas/7/${accion}`);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toBeNull();
    peticion.flush({});
  });

  it('autoAsignar hace POST /auto-asignar con estrategia, categorías y simular', () => {
    const cuerpo = {
      estrategia: 'FALTANTE_META' as const,
      categoriaIds: [7, 8],
      simular: true,
    };

    servicio.autoAsignar(3, '2026-10', cuerpo).subscribe();
    const peticion = backend.expectOne(`${URL_MES}/auto-asignar`);

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(cuerpo);
    peticion.flush({});
  });
});
