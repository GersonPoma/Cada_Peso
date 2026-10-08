import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DatosDialogoMeta, ResultadoDialogoMeta } from '../models/datos-dialogos-metas.model';
import { MetaResponse } from '../models/meta-response.model';
import {
  AYUDA_META,
  DialogoMetaComponent,
  MENSAJE_CONFLICTO_META,
  MENSAJE_META_INEXISTENTE,
  MENSAJE_REGLA_META,
} from './dialogo-meta.component';

const URL = '/api/v1/presupuestos/3/categorias/7/meta';
const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

const META_FECHA: MetaResponse = {
  categoriaId: 7,
  tipo: 'MONTO_PARA_FECHA',
  monto: 600000,
  frecuencia: null,
  diaSemana: null,
  intervaloDias: null,
  fechaInicio: null,
  fechaObjetivo: '2026-12-15',
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoMetaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoDialogoMeta | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(datos: Partial<DatosDialogoMeta> = {}): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoMetaComponent,
      DatosDialogoMeta,
      ResultadoDialogoMeta
    >(DialogoMetaComponent, {
      data: { categoriaId: 7, nombre: 'Comida', tieneMeta: false, ...datos },
    });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function abrirEdicion(meta: MetaResponse = META_FECHA): Promise<void> {
    await abrir({ tieneMeta: true });
    backend.expectOne(URL).flush(meta);
    await estable();
  }

  const campo = (etiqueta: string) =>
    cargador.getHarness(MatFormFieldHarness.with({ floatingLabelText: etiqueta }));
  const hayCampo = async (etiqueta: string) =>
    (await cargador.getAllHarnesses(MatFormFieldHarness.with({ floatingLabelText: etiqueta })))
      .length > 0;
  async function selector(etiqueta: string): Promise<MatSelectHarness> {
    return (await (await campo(etiqueta)).getControl(MatSelectHarness)) as MatSelectHarness;
  }
  async function texto(etiqueta: string): Promise<MatInputHarness> {
    return (await (await campo(etiqueta)).getControl(MatInputHarness)) as MatInputHarness;
  }
  async function opciones(etiqueta: string): Promise<string[]> {
    const select = await selector(etiqueta);
    await select.open();
    const textos = await Promise.all((await select.getOptions()).map((o) => o.getText()));
    // Escape cerraría también el diálogo: se cierra el panel con un clic fuera.
    (document.querySelector('.cdk-overlay-transparent-backdrop') as HTMLElement | null)?.click();
    await estable();
    return textos;
  }
  async function elegir(etiqueta: string, opcion: string): Promise<void> {
    await (await selector(etiqueta)).clickOptions({ text: opcion });
    await estable();
  }
  async function escribir(etiqueta: string, valor: string): Promise<void> {
    const entrada = await texto(etiqueta);
    await entrada.setValue(valor);
    await entrada.blur();
    await estable();
  }
  const boton = (t: string) => cargador.getHarness(MatButtonHarness.with({ text: t }));
  const textoDialogo = () =>
    (document.querySelector('mat-dialog-container')?.textContent ?? '').replace(/[  ]/g, ' ');
  const cargando = () => document.querySelector('mat-dialog-container mat-progress-bar');

  async function guardar(): Promise<TestRequest> {
    await (await boton('Guardar')).click();
    await estable();
    return backend.expectOne(URL);
  }

  beforeEach(() => {
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 8, 12));
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
  });

  afterEach(() => {
    backend.verify();
    TestBed.inject(MatDialog).closeAll();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  describe('agregar', () => {
    it('muestra el nombre de la categoría, la ayuda y los tipos', async () => {
      await abrir();

      expect(document.querySelector('mat-dialog-container h2')?.textContent).toBe('Agregar meta');
      expect(textoDialogo()).toContain('Comida');
      expect(textoDialogo()).toContain(AYUDA_META);
      expect(await opciones('Tipo')).toEqual([
        'Monto cada cierto tiempo',
        'Monto para una fecha',
        'Saldo objetivo',
      ]);
      expect(await opciones('Frecuencia')).toEqual(['Mensual', 'Semanal', 'Personalizada']);
      expect(
        await cargador.getAllHarnesses(MatButtonHarness.with({ text: 'Quitar meta' })),
      ).toHaveLength(0);
      expect(cargando()).toBeNull();
    });

    it('monto mensual envía tipo, monto y frecuencia y cierra con guardada', async () => {
      await abrir();
      await escribir('Monto', '100');
      const peticion = await guardar();

      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({
        tipo: 'MONTO_MENSUAL',
        monto: 100000,
        frecuencia: 'MENSUAL',
      });
      peticion.flush({ ...META_FECHA, tipo: 'MONTO_MENSUAL' });
      await estable();
      expect(resultado).toEqual({ tipo: 'guardada' });
    });

    it('semanal pide el día de la semana (lunes a domingo) y lo envía', async () => {
      await abrir();
      expect(await hayCampo('Día de la semana')).toBe(false);

      await elegir('Frecuencia', 'Semanal');
      expect(await opciones('Día de la semana')).toEqual([
        'Lunes',
        'Martes',
        'Miércoles',
        'Jueves',
        'Viernes',
        'Sábado',
        'Domingo',
      ]);
      await escribir('Monto', '20');
      expect(await (await boton('Guardar')).isDisabled()).toBe(true);

      await elegir('Día de la semana', 'Lunes');
      const peticion = await guardar();
      expect(peticion.request.body).toEqual({
        tipo: 'MONTO_MENSUAL',
        monto: 20000,
        frecuencia: 'SEMANAL',
        diaSemana: 1,
      });
      peticion.flush(META_FECHA);
      await estable();
    });

    it('personalizada valida el intervalo y envía el intervalo y la fecha de inicio', async () => {
      await abrir();
      await elegir('Frecuencia', 'Personalizada');
      await escribir('Monto', '50');

      await escribir('Cada cuántos días', '1');
      expect(await (await campo('Cada cuántos días')).getTextErrors()).toEqual([
        'Entre 2 y 365 días',
      ]);
      expect(await (await boton('Guardar')).isDisabled()).toBe(true);

      await escribir('Cada cuántos días', '366');
      expect(await (await campo('Cada cuántos días')).getTextErrors()).toEqual([
        'Entre 2 y 365 días',
      ]);

      await escribir('Cada cuántos días', '14');
      await escribir('Fecha de inicio', '02/10/2026');
      const peticion = await guardar();
      expect(peticion.request.body).toEqual({
        tipo: 'MONTO_MENSUAL',
        monto: 50000,
        frecuencia: 'PERSONALIZADA',
        intervaloDias: 14,
        fechaInicio: '2026-10-02',
      });
      peticion.flush(META_FECHA);
      await estable();
    });

    it('la fecha de inicio parte de hoy (local)', async () => {
      await abrir();
      await elegir('Frecuencia', 'Personalizada');

      expect(await (await texto('Fecha de inicio')).getValue()).toBe('08/10/2026');
    });

    it('para una fecha envía la fecha objetivo sin frecuencia', async () => {
      await abrir();
      await elegir('Tipo', 'Monto para una fecha');

      expect(await hayCampo('Frecuencia')).toBe(false);
      await escribir('Monto', '600');
      expect(await (await boton('Guardar')).isDisabled()).toBe(true);
      await escribir('Fecha objetivo', '15/12/2026');
      const peticion = await guardar();

      expect(peticion.request.body).toEqual({
        tipo: 'MONTO_PARA_FECHA',
        monto: 600000,
        fechaObjetivo: '2026-12-15',
      });
      peticion.flush(META_FECHA);
      await estable();
    });

    it('una fecha objetivo pasada se admite', async () => {
      await abrir();
      await elegir('Tipo', 'Monto para una fecha');
      await escribir('Monto', '600');
      await escribir('Fecha objetivo', '01/01/2020');
      const peticion = await guardar();

      expect(peticion.request.body.fechaObjetivo).toBe('2020-01-01');
      peticion.flush(META_FECHA);
      await estable();
    });

    it('cambiar a saldo objetivo descarta los campos de la frecuencia', async () => {
      await abrir();
      await elegir('Frecuencia', 'Semanal');
      await elegir('Día de la semana', 'Lunes');
      await escribir('Monto', '300');

      await elegir('Tipo', 'Saldo objetivo');
      expect(await hayCampo('Frecuencia')).toBe(false);
      expect(await hayCampo('Día de la semana')).toBe(false);
      const peticion = await guardar();

      expect(peticion.request.body).toEqual({ tipo: 'SALDO_OBJETIVO', monto: 300000 });
      peticion.flush(META_FECHA);
      await estable();
    });

    it.each(['0', '-5'])('el monto %s es inválido y deshabilita el botón', async (valor) => {
      await abrir();
      await escribir('Monto', valor);

      expect(textoDialogo()).toContain('El monto debe ser mayor que 0');
      expect(await (await boton('Guardar')).isDisabled()).toBe(true);
    });

    it('sin monto el botón está deshabilitado', async () => {
      await abrir();

      expect(await (await boton('Guardar')).isDisabled()).toBe(true);
    });

    it('mientras envía, los botones están deshabilitados', async () => {
      await abrir();
      await escribir('Monto', '100');
      const peticion = await guardar();

      expect(await (await boton('Guardar')).isDisabled()).toBe(true);
      expect(await (await boton('Cancelar')).isDisabled()).toBe(true);
      peticion.flush(META_FECHA);
      await estable();
    });
  });

  describe('editar', () => {
    it('muestra un indicador mientras carga y luego los valores de la meta', async () => {
      await abrir({ tieneMeta: true });

      expect(cargando()).not.toBeNull();
      expect(await (await boton('Guardar')).isDisabled()).toBe(true);
      const peticion = backend.expectOne(URL);
      expect(peticion.request.method).toBe('GET');
      peticion.flush(META_FECHA);
      await estable();

      expect(cargando()).toBeNull();
      expect(document.querySelector('mat-dialog-container h2')?.textContent).toBe('Editar meta');
      expect(await (await selector('Tipo')).getValueText()).toBe('Monto para una fecha');
      expect(await (await texto('Monto')).getValue()).toBe('600');
      expect(await (await texto('Fecha objetivo')).getValue()).toBe('15/12/2026');
    });

    it('carga una meta semanal con su día', async () => {
      await abrirEdicion({
        ...META_FECHA,
        tipo: 'MONTO_MENSUAL',
        monto: 20000,
        frecuencia: 'SEMANAL',
        diaSemana: 3,
        fechaObjetivo: null,
      });

      expect(await (await selector('Frecuencia')).getValueText()).toBe('Semanal');
      expect(await (await selector('Día de la semana')).getValueText()).toBe('Miércoles');
      expect(await hayCampo('Fecha objetivo')).toBe(false);
    });

    it('guardar envía PUT con los valores editados', async () => {
      await abrirEdicion();
      await escribir('Monto', '700');
      const peticion = await guardar();

      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({
        tipo: 'MONTO_PARA_FECHA',
        monto: 700000,
        fechaObjetivo: '2026-12-15',
      });
      peticion.flush(META_FECHA);
      await estable();
      expect(resultado).toEqual({ tipo: 'guardada' });
    });

    it('404 al cargar avisa y cierra pidiendo recargar', async () => {
      await abrir({ tieneMeta: true });
      backend
        .expectOne(URL)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_META_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });
  });

  describe('quitar', () => {
    async function pedirQuitar(): Promise<void> {
      await abrirEdicion();
      await (await boton('Quitar meta')).click();
      await estable();
    }

    it('pide confirmación con el nombre de la categoría', async () => {
      await pedirQuitar();
      const confirmacion = document.querySelectorAll('mat-dialog-container')[1];

      expect(confirmacion?.textContent).toContain(
        'Se quitará la meta de Comida y su estado en todos los meses. ' +
          'El dinero asignado no cambia.',
      );
    });

    it('al confirmar envía DELETE y cierra con quitada', async () => {
      await pedirQuitar();
      await (await boton('Quitar')).click();
      await estable();
      const peticion = backend.expectOne(URL);

      expect(peticion.request.method).toBe('DELETE');
      peticion.flush(null);
      await estable();
      expect(resultado).toEqual({ tipo: 'quitada' });
    });

    it('al cancelar no envía nada y el diálogo sigue abierto', async () => {
      await pedirQuitar();
      const cancelar = await cargador.getAllHarnesses(MatButtonHarness.with({ text: 'Cancelar' }));
      // El último Cancelar es el de la confirmación (encima del diálogo de meta).
      await cancelar[cancelar.length - 1].click();
      await estable();

      backend.expectNone(URL);
      expect(resultado).toBe('abierto');
      expect(document.querySelectorAll('mat-dialog-container')).toHaveLength(1);
    });

    it('404 al quitar avisa y cierra pidiendo recargar', async () => {
      await pedirQuitar();
      await (await boton('Quitar')).click();
      await estable();
      backend
        .expectOne(URL)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_META_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });
  });

  describe('errores al guardar', () => {
    beforeEach(async () => {
      await abrir();
      await elegir('Frecuencia', 'Personalizada');
      await escribir('Monto', '50');
      await escribir('Cada cuántos días', '14');
    });

    it('400 pone el mensaje en su campo', async () => {
      (await guardar()).flush(
        { codigo: 'DATOS_INVALIDOS', errores: { fechaInicio: 'Fecha de inicio inválida' } },
        { status: 400, statusText: 'Bad Request' },
      );
      await estable();

      expect(await (await campo('Fecha de inicio')).getTextErrors()).toEqual([
        'Fecha de inicio inválida',
      ]);
      expect(resultado).toBe('abierto');
    });

    it('409 muestra el conflicto en el diálogo, que sigue abierto', async () => {
      (await guardar()).flush({ codigo: 'CONFLICTO' }, { status: 409, statusText: 'Conflict' });
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_CONFLICTO_META);
      expect(resultado).toBe('abierto');
      expect(await (await boton('Guardar')).isDisabled()).toBe(false);
    });

    it('422 muestra el mensaje en el diálogo, que sigue abierto', async () => {
      (await guardar()).flush(
        { codigo: 'REGLA_NEGOCIO_VIOLADA' },
        { status: 422, statusText: 'Unprocessable' },
      );
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_REGLA_META);
      expect(resultado).toBe('abierto');
    });

    it('404 avisa y cierra pidiendo recargar', async () => {
      (await guardar()).flush(
        { codigo: 'RECURSO_NO_ENCONTRADO' },
        { status: 404, statusText: 'Not Found' },
      );
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_META_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });

    it('otro error avisa de forma genérica', async () => {
      (await guardar()).flush(null, { status: 500, statusText: 'Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toBe('abierto');
    });
  });
});
