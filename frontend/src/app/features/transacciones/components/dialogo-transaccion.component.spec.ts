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
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  DatosDialogoTransaccion,
  ResultadoDialogoTransaccion,
} from '../models/datos-dialogos-transacciones.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import {
  DialogoTransaccionComponent,
  MENSAJE_REFERENCIA_INEXISTENTE,
  MENSAJE_REGLA_TRANSACCION,
} from './dialogo-transaccion.component';

const URL = '/api/v1/presupuestos/3/transacciones';

const CUENTAS = [
  { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false },
  { id: 6, nombre: 'Inversiones', enPresupuesto: false, cerrada: false },
  { id: 9, nombre: 'Vieja', enPresupuesto: true, cerrada: true },
];
const GRUPOS = [
  {
    id: 1,
    nombre: 'Necesidades',
    oculto: false,
    categorias: [
      { id: 7, nombre: 'Comida', oculta: false },
      { id: 8, nombre: 'Ropa', oculta: false },
      { id: 10, nombre: 'Escondida', oculta: true },
    ],
  },
];

function transaccion(cambios: Partial<TransaccionResponse> = {}): TransaccionResponse {
  return {
    id: 40,
    cuentaId: 5,
    fecha: '2026-09-15',
    monto: 100000,
    categoriaId: null,
    beneficiario: 'Empresa',
    memo: 'Sueldo',
    estado: 'NO_CONCILIADA',
    aprobada: true,
    subtransacciones: [],
    transaccionParId: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoTransaccionComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoDialogoTransaccion | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(t: TransaccionResponse | null = null): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoTransaccionComponent,
      DatosDialogoTransaccion,
      ResultadoDialogoTransaccion
    >(DialogoTransaccionComponent, { data: { transaccion: t, cuentas: CUENTAS, grupos: GRUPOS } });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function campo(etiqueta: string) {
    return cargador.getHarness(MatFormFieldHarness.with({ floatingLabelText: etiqueta }));
  }
  async function texto(etiqueta: string): Promise<MatInputHarness> {
    return (await (await campo(etiqueta)).getControl(MatInputHarness)) as MatInputHarness;
  }
  async function selector(etiqueta: string): Promise<MatSelectHarness> {
    return (await (await campo(etiqueta)).getControl(MatSelectHarness)) as MatSelectHarness;
  }
  async function monto(etiqueta: string, valor: string): Promise<void> {
    const entrada = await texto(etiqueta);
    await entrada.setValue(valor);
    await entrada.blur();
    await estable();
  }
  const boton = (t: string) => cargador.getHarness(MatButtonHarness.with({ text: t }));
  const textoDialogo = () =>
    (document.querySelector('mat-dialog-container')?.textContent ?? '').replace(/[  ]/g, ' ');

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 6, 22));
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
  });

  describe('crear', () => {
    beforeEach(() => abrir());

    it('ofrece las cuentas abiertas agrupadas y las categorías visibles', async () => {
      const cuenta = await selector('Cuenta');
      await cuenta.open();
      expect(await Promise.all((await cuenta.getOptions()).map((o) => o.getText()))).toEqual([
        'Banco',
        'Inversiones',
      ]);
      // Elegir una opción cierra el panel (Escape cerraría también el diálogo).
      await cuenta.clickOptions({ text: 'Banco' });

      const categoria = await selector('Categoría');
      await categoria.open();
      expect(await Promise.all((await categoria.getOptions()).map((o) => o.getText()))).toEqual([
        'Sin categoría',
        'Comida',
        'Ropa',
      ]);
    });

    it('una salida se envía en negativo con la fecha local de hoy y aprobada', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await (await selector('Categoría')).clickOptions({ text: 'Comida' });
      await (await texto('Beneficiario')).setValue('  Súper ');
      await monto('Monto', '25,5');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        cuentaId: 5,
        fecha: '2026-10-06',
        monto: -25500,
        categoriaId: 7,
        beneficiario: 'Súper',
        memo: null,
        aprobada: true,
        subtransacciones: [],
      });
      peticion.flush(transaccion());
      await estable();
      expect(resultado).toEqual({ tipo: 'guardada' });
    });

    it('una entrada se envía en positivo y la calculadora suma', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await (await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Entrada' }))).check();
      await monto('Monto', '30+20');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL);
      expect(peticion.request.body.monto).toBe(50000);
      peticion.flush(transaccion());
      await estable();
    });

    it('un monto 0 muestra el error y deshabilita el botón', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await monto('Monto', '0');

      expect(textoDialogo()).toContain('El monto debe ser mayor que 0');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    describe('dividir', () => {
      beforeEach(async () => {
        await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
        await monto('Monto', '30');
        await (await cargador.getHarness(MatSlideToggleHarness)).check();
        await estable();
      });

      it('reemplaza la categoría por dos partes y avisa lo que falta', async () => {
        expect(
          await cargador.getAllHarnesses(
            MatFormFieldHarness.with({ floatingLabelText: 'Categoría' }),
          ),
        ).toHaveLength(0);
        await monto('Monto 1', '10');
        await monto('Monto 2', '15');

        expect(textoDialogo()).toContain('Falta asignar');
        expect(textoDialogo()).toContain('5,00');
        expect(await (await boton('Crear')).isDisabled()).toBe(true);
      });

      it('avisa lo que sobra', async () => {
        await monto('Monto 1', '20');
        await monto('Monto 2', '15');

        expect(textoDialogo()).toContain('Sobra');
      });

      it('con la suma exacta envía las partes con signo y sin categoría propia', async () => {
        await (await selector('Categoría 1')).clickOptions({ text: 'Comida' });
        await monto('Monto 1', '10');
        await monto('Monto 2', '20');
        expect(textoDialogo()).toContain('Las partes suman el monto');
        await (await boton('Crear')).click();

        const peticion = backend.expectOne(URL);
        expect(peticion.request.body.categoriaId).toBeNull();
        expect(peticion.request.body.monto).toBe(-30000);
        expect(peticion.request.body.subtransacciones).toEqual([
          { categoriaId: 7, monto: -10000, memo: null },
          { categoriaId: null, monto: -20000, memo: null },
        ]);
        peticion.flush(transaccion());
        await estable();
      });

      it('no baja de 2 partes ni pasa de 20', async () => {
        const quitar = await cargador.getHarness(
          MatButtonHarness.with({ selector: '[aria-label="Quitar la parte 1"]' }),
        );
        expect(await quitar.isDisabled()).toBe(true);

        const agregar = await cargador.getHarness(MatButtonHarness.with({ text: /Agregar parte/ }));
        for (let i = 0; i < 18; i++) {
          await agregar.click();
        }
        expect(await agregar.isDisabled()).toBe(true);
        expect(document.querySelectorAll('.parte')).toHaveLength(20);
      });
    });

    it('422 muestra el mensaje en el diálogo, que sigue abierto', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await monto('Monto', '5');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL)
        .flush({ codigo: 'REGLA_NEGOCIO_VIOLADA' }, { status: 422, statusText: 'Unprocessable' });
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_REGLA_TRANSACCION);
      expect(resultado).toBe('abierto');
    });

    it('400 muestra el error en su campo', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await monto('Monto', '5');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { memo: 'Memo inválido' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await estable();

      expect(textoDialogo()).toContain('Memo inválido');
      expect(resultado).toBe('abierto');
    });

    it('404 avisa y cierra pidiendo recargar las listas', async () => {
      await (await selector('Cuenta')).clickOptions({ text: 'Banco' });
      await monto('Monto', '5');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_REFERENCIA_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });
  });

  describe('editar', () => {
    it('muestra Entrada y el monto positivo, la cuenta fija, y envía PUT sin cuenta', async () => {
      await abrir(transaccion());

      expect(textoDialogo()).toContain('Editar transacción');
      expect(
        await (
          await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Entrada' }))
        ).isChecked(),
      ).toBe(true);
      expect(await (await texto('Monto')).getValue()).toBe('100');
      const cuenta = await selector('Cuenta');
      expect(await cuenta.isDisabled()).toBe(true);
      expect(await cuenta.getValueText()).toBe('Banco');
      expect(textoDialogo()).not.toContain('Aprobada');

      await (await boton('Guardar')).click();
      const peticion = backend.expectOne(`${URL}/40`);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({
        fecha: '2026-09-15',
        monto: 100000,
        categoriaId: null,
        beneficiario: 'Empresa',
        memo: 'Sueldo',
        subtransacciones: [],
      });
      peticion.flush(transaccion());
      await estable();
    });

    it('una dividida carga sus partes y al desactivar Dividir vuelve a la categoría', async () => {
      await abrir(
        transaccion({
          monto: -30000,
          subtransacciones: [
            { id: 1, categoriaId: 7, monto: -10000, memo: 'a' },
            { id: 2, categoriaId: 10, monto: -20000, memo: null },
          ],
        }),
      );

      expect(await (await texto('Monto 1')).getValue()).toBe('10');
      expect(await (await selector('Categoría 2')).getValueText()).toBe('Escondida');

      await (await cargador.getHarness(MatSlideToggleHarness)).uncheck();
      await estable();
      await (await selector('Categoría')).clickOptions({ text: 'Ropa' });
      await (await boton('Guardar')).click();

      const peticion = backend.expectOne(`${URL}/40`);
      expect(peticion.request.body.categoriaId).toBe(8);
      expect(peticion.request.body.subtransacciones).toEqual([]);
      expect(peticion.request.body.monto).toBe(-30000);
      peticion.flush(transaccion());
      await estable();
    });
  });
});
