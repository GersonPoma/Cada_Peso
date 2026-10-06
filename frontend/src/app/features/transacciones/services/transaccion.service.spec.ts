import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { filtrosVacios } from './filtros-url';
import { TransaccionService } from './transaccion.service';

const URL = '/api/v1/presupuestos/3/transacciones';

describe('TransaccionService', () => {
  let servicio: TransaccionService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(TransaccionService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('listar sin filtros solo manda page y size', () => {
    servicio.listar(3, filtrosVacios()).subscribe();
    const peticion = backend.expectOne((p) => p.url === URL);

    expect(peticion.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(peticion.request.params.get('page')).toBe('0');
    expect(peticion.request.params.get('size')).toBe('20');
    peticion.flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
  });

  it('listar manda cada filtro con su nombre de la API', () => {
    servicio
      .listar(3, {
        cuentaId: 5,
        categoriaId: 7,
        desde: '2026-10-01',
        hasta: '2026-10-31',
        estado: 'CONCILIADA',
        soloSinAprobar: true,
        q: 'super',
        pagina: 2,
        tamano: 50,
      })
      .subscribe();
    const p = backend.expectOne((r) => r.url === URL).request.params;

    expect(p.get('cuentaId')).toBe('5');
    expect(p.get('categoriaId')).toBe('7');
    expect(p.get('desde')).toBe('2026-10-01');
    expect(p.get('hasta')).toBe('2026-10-31');
    expect(p.get('estado')).toBe('CONCILIADA');
    expect(p.get('soloSinAprobar')).toBe('true');
    expect(p.get('q')).toBe('super');
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('50');
  });

  const casos: [string, () => Observable<unknown>, string, string, unknown][] = [
    ['obtener', () => TestBed.inject(TransaccionService).obtener(3, 9), 'GET', `${URL}/9`, null],
    [
      'crear',
      () =>
        TestBed.inject(TransaccionService).crear(3, {
          cuentaId: 5,
          fecha: '2026-10-06',
          monto: -1000,
          categoriaId: null,
          beneficiario: null,
          memo: null,
          aprobada: true,
          subtransacciones: [],
        }),
      'POST',
      URL,
      {
        cuentaId: 5,
        fecha: '2026-10-06',
        monto: -1000,
        categoriaId: null,
        beneficiario: null,
        memo: null,
        aprobada: true,
        subtransacciones: [],
      },
    ],
    [
      'actualizar',
      () =>
        TestBed.inject(TransaccionService).actualizar(3, 9, {
          fecha: '2026-10-06',
          monto: 2000,
          categoriaId: 7,
          beneficiario: 'X',
          memo: null,
          subtransacciones: [],
        }),
      'PUT',
      `${URL}/9`,
      {
        fecha: '2026-10-06',
        monto: 2000,
        categoriaId: 7,
        beneficiario: 'X',
        memo: null,
        subtransacciones: [],
      },
    ],
    ['borrar', () => TestBed.inject(TransaccionService).borrar(3, 9), 'DELETE', `${URL}/9`, null],
    [
      'aprobar',
      () => TestBed.inject(TransaccionService).aprobar(3, 9),
      'POST',
      `${URL}/9/aprobar`,
      null,
    ],
    [
      'cambiarEstado',
      () => TestBed.inject(TransaccionService).cambiarEstado(3, 9, 'CONCILIADA'),
      'PUT',
      `${URL}/9/estado`,
      { estado: 'CONCILIADA' },
    ],
    [
      'moverCuenta',
      () => TestBed.inject(TransaccionService).moverCuenta(3, 9, 6),
      'POST',
      `${URL}/9/mover-cuenta`,
      { cuentaId: 6 },
    ],
    [
      'duplicar',
      () => TestBed.inject(TransaccionService).duplicar(3, 9),
      'POST',
      `${URL}/9/duplicar`,
      null,
    ],
    [
      'lote',
      () =>
        TestBed.inject(TransaccionService).lote(3, {
          ids: [1, 2],
          operacion: 'CATEGORIZAR',
          categoriaId: 7,
        }),
      'POST',
      `${URL}/lote`,
      { ids: [1, 2], operacion: 'CATEGORIZAR', categoriaId: 7 },
    ],
    ['saldos', () => TestBed.inject(TransaccionService).saldos(3), 'GET', `${URL}/saldos`, null],
  ];

  it.each(casos)('%s hace %s %s', (_nombre, llamar, metodo, url, cuerpo) => {
    llamar().subscribe();
    const peticion = backend.expectOne(url);

    expect(servicio).toBeTruthy();
    expect(peticion.request.method).toBe(metodo);
    expect(peticion.request.body).toEqual(cuerpo);
    peticion.flush({});
  });
});
