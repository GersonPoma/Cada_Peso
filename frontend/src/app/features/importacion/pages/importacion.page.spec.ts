import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PaginadorIntl } from '../../../core/material/paginador-intl';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { CuentaImportacion } from '../models/cuenta-importacion.model';
import { VistaPrevia } from '../models/vista-previa-response.model';
import { ImportacionPage } from './importacion.page';

@Component({ template: 'Cuentas' })
class CuentasDePrueba {}

const URL_CUENTA = '/api/v1/presupuestos/3/cuentas/5';
const URL_PREVIA = `${URL_CUENTA}/importacion/vista-previa`;

function cuenta(cambios: Partial<CuentaImportacion> = {}): CuentaImportacion {
  return {
    id: 5,
    nombre: 'Banco',
    tipo: 'CORRIENTE',
    enPresupuesto: true,
    cerrada: false,
    ...cambios,
  };
}

const CSV = [
  'Fecha;Descripción;Monto',
  '13/10/2026;Café;-12,50',
  '14/10/2026;Pizzería;-135,00',
].join('\n');

const RESPUESTA: VistaPrevia = {
  totales: { total: 2, nuevas: 2, duplicadas: 0, invalidas: 0, sinCategoria: 2 },
  filas: [
    {
      fila: 1,
      fecha: '2026-10-13',
      monto: -12500,
      beneficiario: 'Café',
      memo: null,
      estado: 'NUEVA',
      motivo: null,
    },
    {
      fila: 2,
      fecha: '2026-10-14',
      monto: -135000,
      beneficiario: 'Pizzería',
      memo: null,
      estado: 'NUEVA',
      motivo: null,
    },
  ],
};

describe('ImportacionPage', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let aviso: ReturnType<typeof vi.spyOn>;

  const elemento = () => harness.routeNativeElement as HTMLElement;
  const texto = () => (elemento().textContent ?? '').replace(/\s+/g, ' ');
  const pagina = () => harness.routeDebugElement?.componentInstance as Record<string, any>;
  const boton = (clase: string) => elemento().querySelector(`button.${clase}`) as HTMLButtonElement;

  async function estable(): Promise<void> {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
  }

  /** Deja que termine la lectura asíncrona del archivo y pinta el resultado. */
  async function esperarLectura(): Promise<void> {
    for (let i = 0; i < 20 && pagina()['leyendo'](); i++) {
      await new Promise((listo) => setTimeout(listo, 0));
    }
    await estable();
  }

  async function elegir(archivo: File): Promise<void> {
    const entrada = elemento().querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(entrada, 'files', {
      configurable: true,
      value: { item: (i: number) => (i === 0 ? archivo : null), length: 1 },
    });
    entrada.dispatchEvent(new Event('change'));
    await estable();
  }

  async function entrar(datos: CuentaImportacion = cuenta()): Promise<void> {
    await harness.navigateByUrl('/presupuestos/3/cuentas/5/importacion', ImportacionPage);
    backend.expectOne(URL_CUENTA).flush(datos);
    await estable();
  }

  /** Llega al paso del mapeo con el CSV de ejemplo y las tres columnas elegidas. */
  async function hastaElMapeo(): Promise<void> {
    await entrar();
    await elegir(new File([CSV], 'extracto.csv'));
    await esperarLectura();
    boton('continuar').click();
    await estable();
    pagina()['alCambiarMapeo']({
      columnaFecha: 0,
      columnaDescripcion: 1,
      columnaMonto: 2,
      separadorMiles: 'PUNTO',
    });
    await estable();
  }

  async function pedirVistaPrevia(): Promise<TestRequest> {
    boton('pedir').click();
    await estable();
    return backend.expectOne(URL_PREVIA);
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          {
            path: 'presupuestos/:presupuestoId/cuentas/:cuentaId/importacion',
            component: ImportacionPage,
          },
          { path: 'presupuestos/:presupuestoId/cuentas', component: CuentasDePrueba },
        ]),
        { provide: MatPaginatorIntl, useClass: PaginadorIntl },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    aviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('la cuenta', () => {
    it('mientras carga muestra el spinner', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/importacion', ImportacionPage);

      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      backend.expectOne(URL_CUENTA).flush(cuenta());
    });

    it('pide la cuenta y muestra su nombre, su tipo, el enlace a Cuentas y el primer paso', async () => {
      await entrar();

      expect(texto()).toContain('Importar CSV');
      expect(texto()).toContain('Banco');
      expect(texto()).toContain('Corriente');
      expect(elemento().querySelector('a.volver')?.getAttribute('href')).toBe(
        '/presupuestos/3/cuentas',
      );
      expect(texto()).toContain('1. Elige el archivo');
      expect(elemento().querySelector('li[aria-current="step"]')?.textContent).toContain('Archivo');
    });

    it('una cuenta de seguimiento lo dice', async () => {
      await entrar(cuenta({ enPresupuesto: false, tipo: 'TARJETA_CREDITO' }));

      expect(texto()).toContain('Tarjeta de crédito · Seguimiento');
    });

    it('una cuenta cerrada muestra el mensaje y no el asistente', async () => {
      await entrar(cuenta({ nombre: 'Vieja', cerrada: true }));

      expect(texto()).toContain('La cuenta está cerrada; reábrela en Cuentas para importar');
      expect(elemento().querySelector('input[type="file"]')).toBeNull();
      expect(elemento().querySelector('app-indicador-pasos')).toBeNull();
      expect(elemento().querySelector('a.volver')).not.toBeNull();
    });

    it('una cuenta inexistente avisa y lleva a Cuentas', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/importacion', ImportacionPage);
      backend
        .expectOne(URL_CUENTA)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();
      await harness.fixture.whenStable();

      expect(aviso).toHaveBeenCalledWith('No encontramos esa cuenta; vuelve a Cuentas', 'Cerrar', {
        duration: 6000,
      });
      expect(TestBed.inject(Router).url).toBe('/presupuestos/3/cuentas');
    });

    it('un error al cargarla ofrece Reintentar, que la pide otra vez, sin el asistente', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/importacion', ImportacionPage);
      backend.expectOne(URL_CUENTA).flush({}, { status: 500, statusText: 'Server Error' });
      await estable();

      expect(texto()).toContain('No pudimos cargar la cuenta.');
      expect(elemento().querySelector('input[type="file"]')).toBeNull();

      (
        Array.from(elemento().querySelectorAll('button')).find((b) =>
          b.textContent?.includes('Reintentar'),
        ) as HTMLButtonElement
      ).click();
      await estable();
      backend.expectOne(URL_CUENTA).flush(cuenta());
      await estable();

      expect(elemento().querySelector('input[type="file"]')).not.toBeNull();
    });
  });

  describe('leer el archivo sin bloquear', () => {
    beforeEach(() => entrar());

    it('muestra Leyendo el archivo… con Continuar deshabilitado y luego su resumen', async () => {
      await elegir(new File([CSV], 'extracto.csv'));

      expect(texto()).toContain('Leyendo el archivo…');
      expect(boton('continuar').disabled).toBe(true);

      await esperarLectura();

      expect(texto()).not.toContain('Leyendo el archivo…');
      expect(texto()).toContain('extracto.csv');
      expect(texto()).toContain('2 filas de datos');
      expect(boton('continuar').disabled).toBe(false);
    });

    it('un archivo inválido se rechaza con su mensaje sin enviar nada', async () => {
      await elegir(new File([new Uint8Array([0x43, 0x61, 0x66, 0xe9])], 'malo.csv'));
      await esperarLectura();

      expect(texto()).toContain('El archivo debe estar codificado en UTF-8');
      expect(boton('continuar').disabled).toBe(true);
      backend.expectNone(URL_PREVIA);
    });

    it('un archivo de 2 MiB con 5000 filas se acepta y se cuentan, sin ninguna petición', async () => {
      const filas = Array.from(
        { length: 5000 },
        (_, i) => `13/10/2026;Fila ${i};-1,5;${'x'.repeat(370)}`,
      );
      const grande = ['Fecha;Descripción;Monto;Relleno', ...filas].join('\n');
      expect(new TextEncoder().encode(grande).length).toBeLessThanOrEqual(2 * 1024 * 1024);

      await elegir(new File([grande], 'grande.csv'));
      await esperarLectura();

      expect(texto()).toContain('5000 filas de datos');
      expect(boton('continuar').disabled).toBe(false);
      backend.expectNone(URL_PREVIA);
    });

    it('si se elige otro archivo antes de que termine el anterior, solo cuenta el último', async () => {
      await elegir(new File([new Uint8Array([0x43, 0x61, 0x66, 0xe9])], 'malo.csv'));
      await elegir(new File([CSV], 'bueno.csv'));
      await esperarLectura();

      expect(texto()).toContain('bueno.csv');
      expect(texto()).not.toContain('El archivo debe estar codificado en UTF-8');
      expect(pagina()['errorCarga']()).toBeNull();
      expect(boton('continuar').disabled).toBe(false);
    });

    it('nada queda guardado en el navegador', async () => {
      const antes = [Object.keys(localStorage).length, Object.keys(sessionStorage).length];

      await elegir(new File([CSV], 'extracto.csv'));
      await esperarLectura();

      expect([Object.keys(localStorage).length, Object.keys(sessionStorage).length]).toEqual(antes);
    });
  });

  describe('el mapeo', () => {
    beforeEach(() => entrar());

    async function alMapeo(): Promise<void> {
      await elegir(new File([CSV], 'extracto.csv'));
      await esperarLectura();
      boton('continuar').click();
      await estable();
    }

    it('Continuar lleva al mapeo con las sugerencias y el foco en el título', async () => {
      await alMapeo();
      await new Promise((listo) => setTimeout(listo, 0));

      expect(texto()).toContain('2. Elige las columnas');
      expect(elemento().querySelector('li[aria-current="step"]')?.textContent).toContain('Mapeo');
      expect(pagina()['mapeo']()).toMatchObject({
        separador: 'PUNTO_Y_COMA',
        tieneEncabezado: true,
        formatoFecha: 'dd/MM/yyyy',
        separadorDecimal: 'COMA',
      });
      expect(document.activeElement?.tagName).toBe('H2');
      expect(document.activeElement?.textContent).toContain('Elige las columnas');
    });

    it('el análisis no se repite al cambiar el formato de fecha o una columna, pero sí con el separador', async () => {
      await alMapeo();
      const analisis = pagina()['analisis']();

      pagina()['alCambiarMapeo']({ formatoFecha: 'MM/dd/yyyy' });
      pagina()['alCambiarMapeo']({ columnaFecha: 0 });
      expect(pagina()['analisis']()).toBe(analisis);

      pagina()['alCambiarMapeo']({ separador: 'COMA' });
      expect(pagina()['analisis']()).not.toBe(analisis);
    });

    it('al cambiar el separador las columnas que dejan de existir se anulan', async () => {
      await alMapeo();
      pagina()['alCambiarMapeo']({ columnaMonto: 2, columnaDescripcion: 1 });
      await estable();

      pagina()['alCambiarMapeo']({ separador: 'TABULADOR' });
      await estable();

      expect(pagina()['mapeo']().columnaMonto).toBeNull();
      expect(pagina()['mapeo']().columnaDescripcion).toBeNull();
    });

    it('nada se envía mientras se edita el mapeo', async () => {
      await alMapeo();

      pagina()['alCambiarMapeo']({ columnaFecha: 0, columnaMonto: 2, columnaDescripcion: 1 });
      await estable();

      backend.expectNone(URL_PREVIA);
    });
  });

  describe('la vista previa', () => {
    beforeEach(() => hastaElMapeo());

    it('se pide con el botón y lleva el archivo y el mapeo en un multipart', async () => {
      const peticion = await pedirVistaPrevia();
      const cuerpo = peticion.request.body as FormData;
      peticion.flush(RESPUESTA);

      expect(peticion.request.method).toBe('POST');
      expect((cuerpo.get('archivo') as File).name).toBe('extracto.csv');
      expect(cuerpo.get('separador')).toBe('PUNTO_Y_COMA');
      expect(cuerpo.get('columnaFecha')).toBe('0');
      expect(cuerpo.get('columnaMonto')).toBe('2');
      expect(cuerpo.get('columnaDescripcion')).toBe('1');
      expect(cuerpo.get('separadorMiles')).toBe('PUNTO');
      expect(peticion.request.headers.has('Content-Type')).toBe(false);
    });

    it('mientras viaja el botón está deshabilitado', async () => {
      const peticion = await pedirVistaPrevia();

      expect(boton('pedir').disabled).toBe(true);
      expect(texto()).toContain('Pidiendo la vista previa…');
      peticion.flush(RESPUESTA);
    });

    it('al responder muestra los totales y la tabla, y el foco pasa al título', async () => {
      (await pedirVistaPrevia()).flush(RESPUESTA);
      await estable();
      await new Promise((listo) => setTimeout(listo, 0));

      expect(texto()).toContain('3. Revisa la vista previa');
      expect(texto()).toContain('2 filas en total');
      expect(elemento().querySelectorAll('tr[mat-row]')).toHaveLength(2);
      expect(texto()).toContain('Café');
      expect(document.activeElement?.textContent).toContain('Revisa la vista previa');
    });

    it('editar el mapeo mientras viaja cancela la petición y habilita el botón', async () => {
      const peticion = await pedirVistaPrevia();

      pagina()['alCambiarMapeo']({ separadorMiles: 'ESPACIO' });
      await estable();

      expect(peticion.cancelled).toBe(true);
      expect(boton('pedir').disabled).toBe(false);
      expect(texto()).not.toContain('3. Revisa la vista previa');
    });

    it('pedir otra vez después de cancelar solo muestra la respuesta de la última', async () => {
      const primera = await pedirVistaPrevia();
      pagina()['alCambiarMapeo']({ separadorMiles: 'ESPACIO' });
      await estable();
      const segunda = await pedirVistaPrevia();
      segunda.flush({
        ...RESPUESTA,
        totales: { ...RESPUESTA.totales, total: 7, nuevas: 7 },
      });
      await estable();

      expect(primera.cancelled).toBe(true);
      expect(texto()).toContain('7 filas en total');
    });

    it('volver al mapeo con una vista previa vigente ofrece volver a ella sin pedirla', async () => {
      (await pedirVistaPrevia()).flush(RESPUESTA);
      await estable();

      (elemento().querySelector('button.volver') as HTMLButtonElement).click();
      await estable();

      expect(boton('volver-a-vista')).not.toBeNull();
      boton('volver-a-vista').click();
      await estable();
      expect(texto()).toContain('3. Revisa la vista previa');
      backend.expectNone(URL_PREVIA);
    });

    it('editar el mapeo después de la vista previa la marca desactualizada', async () => {
      (await pedirVistaPrevia()).flush(RESPUESTA);
      await estable();
      (elemento().querySelector('button.volver') as HTMLButtonElement).click();
      await estable();

      pagina()['alCambiarMapeo']({ separadorMiles: 'COMA' });
      await estable();

      expect(texto()).toContain('Cambiaste el mapeo: pide la vista previa de nuevo para importar');
      expect(boton('volver-a-vista')).toBeNull();
    });

    it('elegir otro archivo descarta la vista previa anterior', async () => {
      (await pedirVistaPrevia()).flush(RESPUESTA);
      await estable();

      await pagina()['elegirArchivo'](new File([CSV], 'otro.csv'));
      await esperarLectura();

      expect(pagina()['vistaPrevia']()).toBeNull();
    });
  });

  describe('errores de la vista previa', () => {
    beforeEach(() => hastaElMapeo());

    it('un 400 muestra el mensaje fijo, nunca el detail, y conserva el mapeo', async () => {
      (await pedirVistaPrevia()).flush(
        { codigo: 'DATOS_INVALIDOS', detail: 'texto interno del servidor' },
        { status: 400, statusText: 'Bad Request' },
      );
      await estable();

      expect(texto()).toContain(
        'No pudimos leer el archivo con ese mapeo. Revisa que sea un CSV en UTF-8 de hasta 2 MB',
      );
      expect(texto()).not.toContain('texto interno');
      expect(texto()).toContain('2. Elige las columnas');
      expect(pagina()['mapeo']().columnaMonto).toBe(2);
      expect(boton('pedir').disabled).toBe(false);
    });

    it('un 422 con la cuenta cerrada en otra sesión lo dice y el asistente sigue abierto', async () => {
      (await pedirVistaPrevia()).flush(
        { codigo: 'REGLA_NEGOCIO_VIOLADA', detail: 'texto interno' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
      backend.expectOne(URL_CUENTA).flush(cuenta({ cerrada: true }));
      await estable();

      expect(texto()).toContain('La cuenta está cerrada; reábrela en Cuentas para importar');
      expect(texto()).not.toContain('texto interno');
      expect(texto()).toContain('2. Elige las columnas');
    });

    it('un 422 con la cuenta abierta usa el mensaje genérico de reglas', async () => {
      (await pedirVistaPrevia()).flush(
        { codigo: 'REGLA_NEGOCIO_VIOLADA' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
      backend.expectOne(URL_CUENTA).flush(cuenta());
      await estable();

      expect(texto()).toContain(
        'No se pudo importar: el archivo o la cuenta ya no cumplen las reglas',
      );
    });

    it('un 404 avisa y vuelve a pedir la cuenta', async () => {
      (await pedirVistaPrevia()).flush(
        { codigo: 'RECURSO_NO_ENCONTRADO' },
        { status: 404, statusText: 'Not Found' },
      );
      await estable();

      expect(aviso).toHaveBeenCalledWith('No encontramos esa cuenta; vuelve a Cuentas', 'Cerrar', {
        duration: 6000,
      });
      backend.expectOne(URL_CUENTA).flush(cuenta());
    });

    it('otro error muestra el aviso genérico', async () => {
      (await pedirVistaPrevia()).flush({}, { status: 500, statusText: 'Server Error' });
      await estable();

      expect(texto()).toContain('No pudimos completar la operación. Inténtalo de nuevo.');
    });

    it('un 401 no muestra ningún mensaje', async () => {
      (await pedirVistaPrevia()).flush(
        { codigo: 'NO_AUTENTICADO' },
        { status: 401, statusText: 'x' },
      );
      await estable();

      expect(pagina()['errorServidor']()).toBeNull();
    });
  });

  describe('confirmar e importar', () => {
    const URL_IMPORTAR = `${URL_CUENTA}/importacion`;
    const RESULTADO = { importadas: 2, duplicadas: 0, omitidas: 0, sinCategoria: 2 };
    const CON_INVALIDA: VistaPrevia = {
      totales: { total: 3, nuevas: 2, duplicadas: 0, invalidas: 1, sinCategoria: 2 },
      filas: RESPUESTA.filas,
    };

    async function aConfirmar(datos: VistaPrevia = RESPUESTA): Promise<void> {
      await hastaElMapeo();
      (await pedirVistaPrevia()).flush(datos);
      await estable();
      boton('continuar').click();
      await estable();
    }

    async function fallar(estado: number, codigo: string): Promise<void> {
      pagina()['importar'](false);
      backend
        .expectOne(URL_IMPORTAR)
        .flush({ codigo, detail: 'texto interno' }, { status: estado, statusText: 'x' });
      await estable();
    }

    it('Continuar en la vista previa lleva a Confirmar con el foco en el título', async () => {
      await aConfirmar();
      await new Promise((listo) => setTimeout(listo, 0));

      expect(texto()).toContain('4. Confirma la importación');
      expect(document.activeElement?.id).toBe('titulo-paso-confirmar');
    });

    it('Importar envía un POST al endpoint de importación con omitirInvalidas false', async () => {
      await aConfirmar();
      boton('importar').click();
      await estable();

      const peticion = backend.expectOne(URL_IMPORTAR);
      expect((peticion.request.body as FormData).get('omitirInvalidas')).toBe('false');
      expect(boton('importar').disabled).toBe(true);
      peticion.flush(RESULTADO, { status: 201, statusText: 'Created' });
      await estable();

      expect(pagina()['resultado']()).toEqual(RESULTADO);
      expect(pagina()['paso']()).toBe('resultado');
    });

    it('con inválidas marcadas se envía omitirInvalidas true', async () => {
      await aConfirmar(CON_INVALIDA);
      (elemento().querySelector('input[type=checkbox]') as HTMLInputElement).click();
      await estable();
      boton('importar').click();
      await estable();

      const peticion = backend.expectOne(URL_IMPORTAR);
      expect((peticion.request.body as FormData).get('omitirInvalidas')).toBe('true');
      peticion.flush(RESULTADO, { status: 201, statusText: 'Created' });
    });

    it('un segundo envío mientras viaja no manda otra petición', async () => {
      await aConfirmar();
      pagina()['importar'](false);
      pagina()['importar'](false);
      await estable();

      backend.expectOne(URL_IMPORTAR).flush(RESULTADO, { status: 201, statusText: 'Created' });
    });

    it('un 400 muestra el mensaje fijo, nunca el detail, y no cambia de paso', async () => {
      await aConfirmar();
      await fallar(400, 'DATOS_INVALIDOS');

      expect(texto()).toContain('No pudimos leer el archivo con ese mapeo');
      expect(texto()).not.toContain('texto interno');
      expect(pagina()['paso']()).toBe('confirmar');
      expect(boton('importar').disabled).toBe(false);
    });

    it('un 422 con la cuenta cerrada en otra sesión lo dice', async () => {
      await aConfirmar();
      await fallar(422, 'REGLA_NEGOCIO_VIOLADA');
      backend.expectOne(URL_CUENTA).flush(cuenta({ cerrada: true }));
      await estable();

      expect(texto()).toContain('La cuenta está cerrada; reábrela en Cuentas para importar');
      expect(texto()).not.toContain('texto interno');
    });

    it('un 422 con inválidas sin omitir usa el mensaje de filas inválidas', async () => {
      await aConfirmar(CON_INVALIDA);
      // El botón lo impide, pero el servidor es la autoridad: se fuerza el envío sin omitir.
      await fallar(422, 'REGLA_NEGOCIO_VIOLADA');
      backend.expectOne(URL_CUENTA).flush(cuenta());
      await estable();

      expect(pagina()['errorImportacion']()).toContain('El archivo tiene filas inválidas');
    });

    it('un 422 con la cuenta abierta y sin inválidas usa el mensaje genérico', async () => {
      await aConfirmar();
      await fallar(422, 'REGLA_NEGOCIO_VIOLADA');
      backend.expectOne(URL_CUENTA).flush(cuenta());
      await estable();

      expect(texto()).toContain('No se pudo importar: el archivo o la cuenta ya no cumplen');
    });

    it('un 404 avisa y vuelve a pedir la cuenta', async () => {
      await aConfirmar();
      await fallar(404, 'RECURSO_NO_ENCONTRADO');

      expect(aviso).toHaveBeenCalledWith('No encontramos esa cuenta; vuelve a Cuentas', 'Cerrar', {
        duration: 6000,
      });
      backend.expectOne(URL_CUENTA).flush(cuenta());
    });

    it('otro error muestra el aviso genérico y deja reintentar', async () => {
      await aConfirmar();
      await fallar(500, 'OTRO');

      expect(texto()).toContain('No pudimos completar la operación');
      expect(pagina()['importando']()).toBe(false);
    });
  });
});
