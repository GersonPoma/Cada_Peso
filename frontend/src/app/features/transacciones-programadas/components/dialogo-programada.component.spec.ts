import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
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
import {
  DatosDialogoProgramada,
  ResultadoDialogoProgramada,
} from '../models/datos-dialogo-programada.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { TransaccionProgramadaResponse } from '../models/transaccion-programada-response.model';
import {
  AVISO_FIN_DE_MES,
  MENSAJE_FECHAS_MONTO,
  MENSAJE_REFERENCIA_INEXISTENTE,
  MENSAJE_REGLA_PROGRAMADA,
  NOTA_CREAR,
  NOTA_EDITAR,
  NOTA_SOLO_LECTURA,
} from '../services/mensajes-programada';
import { DialogoProgramadaComponent } from './dialogo-programada.component';

const URL = '/api/v1/presupuestos/3/transacciones-programadas';

const CUENTAS = [
  { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false },
  { id: 6, nombre: 'Inversiones', enPresupuesto: false, cerrada: false },
  { id: 9, nombre: 'Vieja', enPresupuesto: true, cerrada: true },
];

const GRUPOS: GrupoCategoriasLectura[] = [
  {
    id: 1,
    nombre: 'Casa',
    oculto: false,
    categorias: [
      { id: 7, nombre: 'Alquiler', oculta: false, esPagoTarjeta: false },
      { id: 8, nombre: 'Escondida', oculta: true, esPagoTarjeta: false },
    ],
  },
  {
    id: 2,
    nombre: 'Pagos de tarjeta',
    oculto: false,
    categorias: [{ id: 20, nombre: 'Pago: Visa', oculta: false, esPagoTarjeta: true }],
  },
];

function programada(
  cambios: Partial<TransaccionProgramadaResponse> = {},
): TransaccionProgramadaResponse {
  return {
    id: 12,
    cuentaId: 9,
    fechaInicio: '2026-11-05',
    frecuencia: 'MENSUAL',
    fechaFin: null,
    monto: -1500000,
    categoriaId: 8,
    beneficiario: 'Inmobiliaria',
    memo: 'Alquiler',
    activa: true,
    proximaFecha: '2026-11-05',
    ultimoError: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoProgramadaComponent', { timeout: 30000 }, () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoDialogoProgramada | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(original: TransaccionProgramadaResponse | null = null): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoProgramadaComponent,
      DatosDialogoProgramada,
      ResultadoDialogoProgramada
    >(DialogoProgramadaComponent, { data: { cuentas: CUENTAS, grupos: GRUPOS, original } });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  const campo = (etiqueta: string) =>
    cargador.getHarness(MatFormFieldHarness.with({ floatingLabelText: etiqueta }));
  const texto = async (etiqueta: string) =>
    (await (await campo(etiqueta)).getControl(MatInputHarness)) as MatInputHarness;
  const selector = async (etiqueta: string) =>
    (await (await campo(etiqueta)).getControl(MatSelectHarness)) as MatSelectHarness;
  const boton = (t: string) => cargador.getHarness(MatButtonHarness.with({ text: t }));
  const textoDialogo = () =>
    (document.querySelector('mat-dialog-container')?.textContent ?? '')
      .replace(/[  ]/g, ' ')
      .replace(/\s+/g, ' ');
  const opciones = async (s: MatSelectHarness) => {
    await s.open();
    return Promise.all((await s.getOptions()).map((o) => o.getText()));
  };

  async function escribir(etiqueta: string, valor: string): Promise<void> {
    const entrada = await texto(etiqueta);
    await entrada.setValue(valor);
    await entrada.blur();
    await estable();
  }

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 8, 22));
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
  });

  describe('crear', () => {
    beforeEach(() => abrir());

    it('explica que crearla no genera nada y usa los valores por defecto', async () => {
      expect(textoDialogo()).toContain(NOTA_CREAR);
      expect(await (await selector('Frecuencia')).getValueText()).toBe('Cada mes');
      expect(await (await texto('Fecha de inicio')).getValue()).toBe('08/10/2026');
      expect(
        await (
          await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Salida' }))
        ).isChecked(),
      ).toBe(true);
    });

    it('ofrece solo las cuentas abiertas y las categorías admitidas', async () => {
      expect(await opciones(await selector('Cuenta'))).toEqual(['Banco', 'Inversiones']);
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      expect(await opciones(await selector('Categoría'))).toEqual(['Sin categoría', 'Alquiler']);
    });

    it('una salida mensual se envía en negativo con la fecha local', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Fecha de inicio', '5/11/2026');
      await escribir('Monto', '1500');
      await (await selector('Categoría')).clickOptions({ text: 'Alquiler' });
      await (await texto('Beneficiario')).setValue('  Inmobiliaria ');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        cuentaId: 5,
        fechaInicio: '2026-11-05',
        frecuencia: 'MENSUAL',
        fechaFin: null,
        monto: -1500000,
        categoriaId: 7,
        beneficiario: 'Inmobiliaria',
        memo: null,
      });
      peticion.flush(programada());
      await estable();
      expect(resultado).toBe('guardada');
    });

    it('una entrada con expresión se envía en positivo', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await (await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Entrada' }))).check();
      await escribir('Monto', '30+20,5');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL);
      expect(peticion.request.body.monto).toBe(50500);
      peticion.flush(programada());
      await estable();
    });

    it('una fecha de fin anterior al inicio se explica y no se envía', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Monto', '10');
      await escribir('Fecha de inicio', '5/11/2026');
      await escribir('Fecha de fin (opcional)', '1/11/2026');

      expect(textoDialogo()).toContain('La fecha de fin no puede ser anterior a la de inicio');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('avisa la regla de fin de mes con día 31 en una frecuencia por meses', async () => {
      expect(textoDialogo()).not.toContain(AVISO_FIN_DE_MES);
      await escribir('Fecha de inicio', '31/1/2027');
      expect(textoDialogo()).toContain(AVISO_FIN_DE_MES);

      await (await selector('Frecuencia')).clickOptions({ text: 'Cada semana' });
      await estable();
      expect(textoDialogo()).not.toContain(AVISO_FIN_DE_MES);
    });

    it('un doble clic envía una sola petición', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Monto', '10');
      const crear = await boton('Crear');
      await crear.click();
      await crear.click();

      expect(backend.match(URL)).toHaveLength(1);
    });

    it('un 422 se muestra en el diálogo sin cerrarlo ni perder lo escrito', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Monto', '10');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL)
        .flush({ codigo: 'REGLA_NEGOCIO_VIOLADA' }, { status: 422, statusText: 'Unprocessable' });
      await estable();

      expect(resultado).toBe('abierto');
      expect(textoDialogo()).toContain(MENSAJE_REGLA_PROGRAMADA);
      expect(await (await texto('Monto')).getValue()).toContain('10');
      expect(await (await boton('Crear')).isDisabled()).toBe(false);
    });

    it('un 400 con errores los pone en su campo', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Monto', '10');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { memo: 'El memo es demasiado largo' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await estable();

      expect(await (await campo('Memo')).getTextErrors()).toEqual(['El memo es demasiado largo']);
    });

    it('un 400 sin errores pide revisar fechas y monto', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Monto', '10');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL)
        .flush({ codigo: 'DATOS_INVALIDOS' }, { status: 400, statusText: 'Bad Request' });
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_FECHAS_MONTO);
    });

    it('otro error muestra el aviso genérico sin cerrar', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await escribir('Monto', '10');
      await (await boton('Crear')).click();
      backend.expectOne(URL).error(new ProgressEvent('error'));
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toBe('abierto');
    });
  });

  describe('editar', () => {
    beforeEach(() => abrir(programada()));

    it('muestra la cuenta (aunque esté cerrada) y la fecha de inicio en solo lectura', () => {
      const datos = Array.from(document.querySelectorAll('.solo-lectura dd')).map((dd) =>
        dd.textContent?.trim(),
      );
      expect(datos).toEqual(['Vieja', '5 de noviembre de 2026']);
      const textoActual = textoDialogo();
      expect(textoActual).toContain(NOTA_SOLO_LECTURA);
      expect(textoActual).toContain(NOTA_EDITAR);
      expect(
        document.querySelector('mat-dialog-container [formcontrolname="cuentaId"]'),
      ).toBeNull();
    });

    it('conserva la categoría actual aunque esté oculta', async () => {
      expect(await (await selector('Categoría')).getValueText()).toBe('Escondida');
    });

    it('envía PUT sin la cuenta ni la fecha de inicio', async () => {
      await escribir('Monto', '1600');
      await (await boton('Guardar')).click();

      const peticion = backend.expectOne(`${URL}/12`);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({
        monto: -1600000,
        categoriaId: 8,
        beneficiario: 'Inmobiliaria',
        memo: 'Alquiler',
        frecuencia: 'MENSUAL',
        fechaFin: null,
      });
      peticion.flush(programada({ monto: -1600000 }));
      await estable();
      expect(resultado).toBe('guardada');
    });

    it('la fecha de fin se compara con la fecha de inicio guardada', async () => {
      await escribir('Fecha de fin (opcional)', '1/11/2026');

      expect(textoDialogo()).toContain('La fecha de fin no puede ser anterior a la de inicio');
    });

    it('un 404 cierra el diálogo, avisa y pide recargar', async () => {
      await (await boton('Guardar')).click();
      backend
        .expectOne(`${URL}/12`)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(resultado).toBe('recargar');
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_REFERENCIA_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
    });
  });
});
