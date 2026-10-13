import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ImportacionResponse } from '../models/importacion-response.model';
import { MapeoImportacion } from '../models/mapeo-importacion.model';
import { VistaPrevia } from '../models/vista-previa-response.model';
import { ImportacionService } from './importacion.service';

const MAPEO: MapeoImportacion = {
  separador: 'COMA',
  tieneEncabezado: true,
  columnaFecha: 0,
  formatoFecha: 'dd/MM/yyyy',
  modoMonto: 'UNA_COLUMNA',
  columnaMonto: 2,
  columnaDebito: null,
  columnaCredito: null,
  separadorDecimal: 'COMA',
  separadorMiles: 'PUNTO',
  columnaDescripcion: 1,
  columnaMemo: null,
};

const URL_BASE = '/api/v1/presupuestos/3/cuentas/5/importacion';

describe('ImportacionService', () => {
  let backend: HttpTestingController;
  let servicio: ImportacionService;
  const archivo = () => new File(['fecha,desc,monto\n13/10/2026,Cafe,-12,5'], 'extracto.csv');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ImportacionService);
  });

  afterEach(() => backend.verify());

  describe('vistaPrevia', () => {
    it('hace POST a vista-previa con el archivo y el mapeo en un FormData', () => {
      servicio.vistaPrevia(3, 5, archivo(), MAPEO).subscribe();

      const peticion = backend.expectOne(`${URL_BASE}/vista-previa`);
      const cuerpo = peticion.request.body as FormData;
      peticion.flush({ totales: {}, filas: [] });

      expect(peticion.request.method).toBe('POST');
      expect(cuerpo).toBeInstanceOf(FormData);
      expect((cuerpo.get('archivo') as File).name).toBe('extracto.csv');
      expect(cuerpo.get('separador')).toBe('COMA');
      expect(cuerpo.get('formatoFecha')).toBe('dd/MM/yyyy');
      expect(cuerpo.get('columnaMonto')).toBe('2');
    });

    it('no fija el Content-Type a mano ni manda omitirInvalidas', () => {
      servicio.vistaPrevia(3, 5, archivo(), MAPEO).subscribe();

      const peticion = backend.expectOne(`${URL_BASE}/vista-previa`);
      peticion.flush({ totales: {}, filas: [] });

      expect(peticion.request.headers.has('Content-Type')).toBe(false);
      expect((peticion.request.body as FormData).has('omitirInvalidas')).toBe(false);
    });

    it('devuelve la vista previa del servidor', () => {
      const respuesta: VistaPrevia = {
        totales: { total: 1, nuevas: 1, duplicadas: 0, invalidas: 0, sinCategoria: 1 },
        filas: [
          {
            fila: 1,
            fecha: '2026-10-13',
            monto: -12500,
            beneficiario: 'Cafe',
            memo: null,
            estado: 'NUEVA',
            motivo: null,
          },
        ],
      };
      let recibida: VistaPrevia | undefined;
      servicio.vistaPrevia(3, 5, archivo(), MAPEO).subscribe((r) => (recibida = r));

      backend.expectOne(`${URL_BASE}/vista-previa`).flush(respuesta);

      expect(recibida).toEqual(respuesta);
    });

    it('un 400 llega como error', () => {
      let status = 0;
      servicio
        .vistaPrevia(3, 5, archivo(), MAPEO)
        .subscribe({ error: (e: { status: number }) => (status = e.status) });

      backend
        .expectOne(`${URL_BASE}/vista-previa`)
        .flush({ codigo: 'DATOS_INVALIDOS' }, { status: 400, statusText: 'Bad Request' });

      expect(status).toBe(400);
    });
  });

  describe('importar', () => {
    it('hace POST al endpoint de importación (no al de la vista previa) con omitirInvalidas', () => {
      servicio.importar(3, 5, archivo(), MAPEO, true).subscribe();

      const peticion = backend.expectOne(URL_BASE);
      const cuerpo = peticion.request.body as FormData;
      peticion.flush({ importadas: 0, duplicadas: 0, omitidas: 0, sinCategoria: 0 });

      expect(peticion.request.method).toBe('POST');
      expect(cuerpo).toBeInstanceOf(FormData);
      expect(cuerpo.get('omitirInvalidas')).toBe('true');
      expect((cuerpo.get('archivo') as File).name).toBe('extracto.csv');
      expect(cuerpo.get('formatoFecha')).toBe('dd/MM/yyyy');
    });

    it('omitirInvalidas false viaja como false y no se fija el Content-Type', () => {
      servicio.importar(3, 5, archivo(), MAPEO, false).subscribe();

      const peticion = backend.expectOne(URL_BASE);
      peticion.flush({ importadas: 0, duplicadas: 0, omitidas: 0, sinCategoria: 0 });

      expect((peticion.request.body as FormData).get('omitirInvalidas')).toBe('false');
      expect(peticion.request.headers.has('Content-Type')).toBe(false);
    });

    it('devuelve las cifras del 201', () => {
      const respuesta: ImportacionResponse = {
        importadas: 2,
        duplicadas: 3,
        omitidas: 1,
        sinCategoria: 2,
      };
      let recibida: ImportacionResponse | undefined;
      servicio.importar(3, 5, archivo(), MAPEO, true).subscribe((r) => (recibida = r));

      backend.expectOne(URL_BASE).flush(respuesta, { status: 201, statusText: 'Created' });

      expect(recibida).toEqual(respuesta);
    });

    it('un 422 llega como error', () => {
      let status = 0;
      servicio
        .importar(3, 5, archivo(), MAPEO, false)
        .subscribe({ error: (e: { status: number }) => (status = e.status) });

      backend
        .expectOne(URL_BASE)
        .flush({ codigo: 'REGLA_NEGOCIO_VIOLADA' }, { status: 422, statusText: 'Unprocessable' });

      expect(status).toBe(422);
    });
  });
});
