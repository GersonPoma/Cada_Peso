import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import {
  DatosDialogoTransferencia,
  ResultadoDialogoTransaccion,
} from '../models/datos-dialogos-transacciones.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import {
  DialogoTransferenciaComponent,
  MENSAJE_REGLA_TRANSFERENCIA,
  MENSAJE_TRANSFERENCIA_INEXISTENTE,
  TEXTOS_REGLA_CATEGORIA,
} from './dialogo-transferencia.component';

const URL = '/api/v1/presupuestos/3/transferencias';
const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

const CUENTAS = [
  { id: 1, nombre: 'Banco', enPresupuesto: true, cerrada: false },
  { id: 2, nombre: 'Ahorro', enPresupuesto: true, cerrada: false },
  { id: 3, nombre: 'Visa', enPresupuesto: false, cerrada: false },
  { id: 4, nombre: 'Inversiones', enPresupuesto: false, cerrada: false },
  { id: 5, nombre: 'Hipoteca', enPresupuesto: false, cerrada: false },
  { id: 9, nombre: 'Vieja', enPresupuesto: true, cerrada: true },
];
const GRUPOS = [
  {
    id: 1,
    nombre: 'Metas',
    oculto: false,
    categorias: [
      { id: 7, nombre: 'Ahorro largo plazo', oculta: false },
      { id: 8, nombre: 'Comida', oculta: false },
      { id: 10, nombre: 'Escondida', oculta: true },
    ],
  },
];

function pata(cambios: Partial<TransaccionResponse>): TransaccionResponse {
  return {
    id: 20,
    cuentaId: 1,
    fecha: '2026-10-01',
    monto: -50000,
    categoriaId: null,
    beneficiario: null,
    beneficiarioId: null,
    memo: 'Cuota',
    estado: 'NO_CONCILIADA',
    aprobada: true,
    subtransacciones: [],
    transaccionParId: 21,
    programadaId: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}
const SALIDA = pata({ id: 20, cuentaId: 1, monto: -50000, categoriaId: 7, transaccionParId: 21 });
const ENTRADA = pata({ id: 21, cuentaId: 3, monto: 50000, transaccionParId: 20 });

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoTransferenciaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoDialogoTransaccion | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(datos: Partial<DatosDialogoTransferencia> = {}): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoTransferenciaComponent,
      DatosDialogoTransferencia,
      ResultadoDialogoTransaccion
    >(DialogoTransferenciaComponent, {
      data: {
        transaccionId: null,
        cuentaOrigenId: null,
        cuentas: CUENTAS,
        grupos: GRUPOS,
        ...datos,
      },
    });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
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
  async function cuentas(origen: string, destino: string): Promise<void> {
    await elegir('Cuenta origen', origen);
    await elegir('Cuenta destino', destino);
  }
  async function monto(valor: string): Promise<void> {
    const entrada = await texto('Monto');
    await entrada.setValue(valor);
    await entrada.blur();
    await estable();
  }
  const boton = (t: string) => cargador.getHarness(MatButtonHarness.with({ text: t }));
  const invertir = () =>
    cargador.getAllHarnesses(MatButtonHarness.with({ selector: '[aria-label="Invertir"]' }));
  const textoDialogo = () =>
    (document.querySelector('mat-dialog-container')?.textContent ?? '').replace(/[  ]/g, ' ');

  async function crear() {
    await (await boton('Crear')).click();
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
        provideRouter([]),
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

  describe('crear', () => {
    it('entre dos cuentas del presupuesto envía sin categoriaId y con la fecha de hoy', async () => {
      await abrir();
      await cuentas('Banco', 'Ahorro');
      await monto('150');
      const peticion = await crear();

      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        cuentaOrigenId: 1,
        cuentaDestinoId: 2,
        fecha: '2026-10-08',
        monto: 150000,
        memo: null,
      });
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
      expect(resultado).toEqual({ tipo: 'guardada' });
    });

    it('solo ofrece cuentas abiertas y cada select excluye la del otro', async () => {
      await abrir();
      expect(await opciones('Cuenta origen')).toEqual([
        'Banco',
        'Ahorro',
        'Visa',
        'Inversiones',
        'Hipoteca',
      ]);

      await elegir('Cuenta origen', 'Banco');
      expect(await opciones('Cuenta destino')).toEqual([
        'Ahorro',
        'Visa',
        'Inversiones',
        'Hipoteca',
      ]);
    });

    it('agrupa en En el presupuesto y Seguimiento', async () => {
      await abrir();
      const select = await selector('Cuenta origen');
      await select.open();
      const etiquetas = (await select.getOptionGroups()).map((g) => g.getLabelText());

      expect(await Promise.all(etiquetas)).toEqual(['En el presupuesto', 'Seguimiento']);
      await select.clickOptions({ text: 'Banco' });
    });

    it('Invertir intercambia origen y destino', async () => {
      await abrir();
      await cuentas('Banco', 'Visa');
      await (await invertir())[0].click();
      await estable();

      expect(await (await selector('Cuenta origen')).getValueText()).toBe('Visa');
      expect(await (await selector('Cuenta destino')).getValueText()).toBe('Banco');
    });

    it('preselecciona la cuenta origen si está abierta', async () => {
      await abrir({ cuentaOrigenId: 1 });

      expect(await (await selector('Cuenta origen')).getValueText()).toBe('Banco');
      expect(await (await selector('Cuenta destino')).getValueText()).toBe('');
    });

    it('no preselecciona una cuenta cerrada', async () => {
      await abrir({ cuentaOrigenId: 9 });

      expect(await (await selector('Cuenta origen')).getValueText()).toBe('');
    });

    it.each(['0', '-5'])('el monto %s es inválido y deshabilita el botón', async (valor) => {
      await abrir();
      await cuentas('Banco', 'Ahorro');
      await monto(valor);

      expect(textoDialogo()).toContain('El monto debe ser mayor que 0');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('envía el memo recortado', async () => {
      await abrir();
      await cuentas('Banco', 'Ahorro');
      await monto('10');
      await (await texto('Memo')).setValue('  Ahorro mensual ');
      const peticion = await crear();

      expect(peticion.request.body.memo).toBe('Ahorro mensual');
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
    });

    it.each([
      // Día y hora locales: el Date se arma después de cambiar la zona horaria.
      ['America/New_York', 8, 23, '2026-10-08'],
      ['Asia/Tokyo', 9, 1, '2026-10-09'],
    ])('en %s envía la fecha local, no la de UTC', async (zona, dia, hora, esperada) => {
      process.env['TZ'] = zona;
      vi.setSystemTime(new Date(2026, 9, dia, hora));
      await abrir();
      await cuentas('Banco', 'Ahorro');
      await monto('10');
      const peticion = await crear();

      expect(peticion.request.body.fecha).toBe(esperada);
      expect(new Date().toISOString().slice(0, 10)).not.toBe(esperada);
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
    });
  });

  describe('categoría', () => {
    beforeEach(() => abrir());

    it('sin las dos cuentas no hay campo de categoría', async () => {
      expect(await hayCampo('Categoría')).toBe(false);
      await elegir('Cuenta origen', 'Banco');
      expect(await hayCampo('Categoría')).toBe(false);
    });

    it('ambas de seguimiento: oculta, con su texto y sin categoriaId', async () => {
      await cuentas('Hipoteca', 'Inversiones');
      await monto('10');

      expect(await hayCampo('Categoría')).toBe(false);
      expect(textoDialogo()).toContain(TEXTOS_REGLA_CATEGORIA['oculta-seguimiento']);
      const peticion = await crear();
      expect('categoriaId' in peticion.request.body).toBe(false);
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
    });

    it('ambas del presupuesto: muestra el texto de cuentas del presupuesto', async () => {
      await cuentas('Banco', 'Ahorro');

      expect(await hayCampo('Categoría')).toBe(false);
      expect(textoDialogo()).toContain(TEXTOS_REGLA_CATEGORIA['oculta-presupuesto']);
    });

    it('del presupuesto a seguimiento: obligatoria con su ayuda', async () => {
      await cuentas('Banco', 'Inversiones');
      await monto('10');

      expect(textoDialogo()).toContain('Este dinero sale del presupuesto');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
      expect(await opciones('Categoría')).toEqual(['Ahorro largo plazo', 'Comida']);

      await elegir('Categoría', 'Ahorro largo plazo');
      const peticion = await crear();
      expect(peticion.request.body.categoriaId).toBe(7);
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
    });

    it('de seguimiento al presupuesto: opcional, envía categoriaId null', async () => {
      await cuentas('Inversiones', 'Banco');
      await monto('10');

      expect(textoDialogo()).toContain(TEXTOS_REGLA_CATEGORIA.opcional);
      const peticion = await crear();
      expect(peticion.request.body.categoriaId).toBeNull();
      expect('categoriaId' in peticion.request.body).toBe(true);
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
    });

    it('al cambiar a cuentas sin categoría se vacía y no vuelve', async () => {
      await cuentas('Banco', 'Inversiones');
      await elegir('Categoría', 'Ahorro largo plazo');

      await elegir('Cuenta destino', 'Ahorro');
      expect(await hayCampo('Categoría')).toBe(false);

      await elegir('Cuenta destino', 'Inversiones');
      expect(await (await selector('Categoría')).getValueText()).toBe('');
    });
  });

  describe('editar', () => {
    async function abrirEdicion(): Promise<void> {
      await abrir({ transaccionId: 21 });
      backend.expectOne(`${URL}/21`).flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
    }

    it('muestra un indicador mientras carga', async () => {
      await abrir({ transaccionId: 21 });

      expect(document.querySelector('mat-dialog-container mat-progress-spinner')).not.toBeNull();
      backend.expectOne(`${URL}/21`).flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
      expect(document.querySelector('mat-dialog-container mat-progress-spinner')).toBeNull();
    });

    it('pide la transferencia con el id de la pata y carga sus valores', async () => {
      await abrirEdicion();

      const origen = await selector('Cuenta origen');
      const destino = await selector('Cuenta destino');
      expect(await origen.getValueText()).toBe('Banco');
      expect(await destino.getValueText()).toBe('Visa');
      expect(await origen.isDisabled()).toBe(true);
      expect(await destino.isDisabled()).toBe(true);
      expect(await invertir()).toHaveLength(0);
      expect(await (await texto('Monto')).getValue()).toBe('50');
      expect(await (await selector('Categoría')).getValueText()).toBe('Ahorro largo plazo');
      expect(await (await texto('Memo')).getValue()).toBe('Cuota');
    });

    it('una categoría oculta ya elegida se muestra', async () => {
      await abrir({ transaccionId: 21 });
      backend
        .expectOne(`${URL}/21`)
        .flush({ salida: { ...SALIDA, categoriaId: 10 }, entrada: ENTRADA });
      await estable();

      expect(await (await selector('Categoría')).getValueText()).toBe('Escondida');
    });

    it('guardar envía PUT con el id de la pata y sin cuentas', async () => {
      await abrirEdicion();
      await monto('60');
      await (await boton('Guardar')).click();
      const peticion = backend.expectOne(`${URL}/21`);

      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({
        fecha: '2026-10-01',
        monto: 60000,
        categoriaId: 7,
        memo: 'Cuota',
      });
      peticion.flush({ salida: SALIDA, entrada: ENTRADA });
      await estable();
      expect(resultado).toEqual({ tipo: 'guardada' });
    });

    it('404 al cargar avisa y cierra pidiendo recargar', async () => {
      await abrir({ transaccionId: 21 });
      backend
        .expectOne(`${URL}/21`)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_TRANSFERENCIA_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });

    it('404 al guardar avisa y cierra pidiendo recargar', async () => {
      await abrirEdicion();
      await (await boton('Guardar')).click();
      backend
        .expectOne(`${URL}/21`)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(resultado).toEqual({ tipo: 'recargar' });
    });
  });

  describe('errores al crear', () => {
    beforeEach(async () => {
      await abrir();
      await cuentas('Banco', 'Ahorro');
      await monto('10');
    });

    it('400 pone el mensaje en su campo', async () => {
      (await crear()).flush(
        { codigo: 'DATOS_INVALIDOS', errores: { memo: 'Memo inválido' } },
        { status: 400, statusText: 'Bad Request' },
      );
      await estable();

      expect(await (await campo('Memo')).getTextErrors()).toEqual(['Memo inválido']);
      expect(resultado).toBe('abierto');
    });

    it('422 muestra el mensaje en el diálogo, que sigue abierto', async () => {
      (await crear()).flush(
        { codigo: 'REGLA_NEGOCIO_VIOLADA' },
        { status: 422, statusText: 'Unprocessable' },
      );
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_REGLA_TRANSFERENCIA);
      expect(resultado).toBe('abierto');
    });

    it('otro error avisa de forma genérica', async () => {
      (await crear()).flush(null, { status: 500, statusText: 'Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toBe('abierto');
    });
  });
});
