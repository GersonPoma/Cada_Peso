import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
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
import { CuentaResponse } from '../models/cuenta-response.model';
import { DatosDialogoCuenta } from '../models/datos-dialogo-cuenta.model';
import { DialogoCuentaComponent, MENSAJE_SALDO_NEGATIVO } from './dialogo-cuenta.component';

const URL_CUENTAS = '/api/v1/presupuestos/3/cuentas';

function cuenta(cambios: Partial<CuentaResponse> = {}): CuentaResponse {
  return {
    id: 5,
    nombre: 'Banco',
    tipo: 'CORRIENTE',
    enPresupuesto: true,
    saldoInicial: 0,
    cerrada: false,
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
    ...cambios,
  };
}

/** Componente vacío: solo da un fixture para cargar los arneses del overlay del diálogo. */
@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoCuentaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: CuentaResponse | undefined | 'abierto';

  async function abrir(datos: DatosDialogoCuenta, region = 'es-BO'): Promise<void> {
    vi.stubGlobal('navigator', { language: region, userAgent: '' });
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoCuentaComponent,
      DatosDialogoCuenta,
      CuentaResponse
    >(DialogoCuentaComponent, { data: datos });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const textoDialogo = () => document.querySelector('mat-dialog-container')?.textContent ?? '';
  async function campo(etiqueta: string): Promise<MatInputHarness> {
    const formField = await cargador.getHarness(
      MatFormFieldHarness.with({ floatingLabelText: etiqueta }),
    );
    const control = await formField.getControl(MatInputHarness);
    if (!control) {
      throw new Error(`El campo ${etiqueta} no es un input`);
    }
    return control;
  }
  const nombre = () => campo('Nombre');
  const saldo = () => campo('Saldo inicial');
  const tipo = () => cargador.getHarness(MatSelectHarness);
  const boton = (texto: string) => cargador.getHarness(MatButtonHarness.with({ text: texto }));
  const errores = () =>
    Array.from(document.querySelectorAll('mat-dialog-container mat-error')).map((e) =>
      e.textContent?.trim(),
    );

  async function escribirSaldo(texto: string): Promise<void> {
    const entrada = await saldo();
    await entrada.setValue(texto);
    await entrada.blur();
  }

  beforeEach(() => {
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
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('crear', () => {
    it('muestra los campos con sus valores por defecto', async () => {
      await abrir({ modo: 'crear' });

      expect(textoDialogo()).toContain('Nueva cuenta');
      expect(await (await nombre()).getValue()).toBe('');
      expect(await (await tipo()).getValueText()).toBe('');
      const casilla = await cargador.getHarness(MatCheckboxHarness);
      expect(await casilla.getLabelText()).toBe('Cuenta del presupuesto');
      expect(await casilla.isChecked()).toBe(true);
      expect(await (await saldo()).getValue()).toBe('0');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('ofrece los tipos en español', async () => {
      await abrir({ modo: 'crear' });
      const selector = await tipo();
      await selector.open();
      const opciones = await Promise.all((await selector.getOptions()).map((o) => o.getText()));

      expect(opciones).toEqual([
        'Corriente',
        'Ahorro',
        'Efectivo',
        'Tarjeta de crédito',
        'Inversión',
        'Préstamo',
      ]);
    });

    it('con los valores por defecto envía el nombre recortado y saldo 0', async () => {
      await abrir({ modo: 'crear' });
      await (await nombre()).setValue('  Efectivo ');
      await (await tipo()).clickOptions({ text: 'Efectivo' });
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL_CUENTAS);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        nombre: 'Efectivo',
        tipo: 'EFECTIVO',
        enPresupuesto: true,
        saldoInicial: 0,
      });
      peticion.flush(cuenta({ nombre: 'Efectivo', tipo: 'EFECTIVO' }));
      await estable();

      expect(resultado).toEqual(cuenta({ nombre: 'Efectivo', tipo: 'EFECTIVO' }));
    });

    it('una cuenta de seguimiento envía enPresupuesto falso y el saldo en milésimas', async () => {
      await abrir({ modo: 'crear' }, 'es-BO');
      await (await nombre()).setValue('Inversiones');
      await (await tipo()).clickOptions({ text: 'Inversión' });
      await (await cargador.getHarness(MatCheckboxHarness)).uncheck();
      await escribirSaldo('5.000,5');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL_CUENTAS);
      expect(peticion.request.body).toEqual({
        nombre: 'Inversiones',
        tipo: 'INVERSION',
        enPresupuesto: false,
        saldoInicial: 5000500,
      });
      peticion.flush(cuenta());
      await estable();
    });

    it('un saldo vacío se envía como 0', async () => {
      await abrir({ modo: 'crear' });
      await (await nombre()).setValue('Banco');
      await (await tipo()).clickOptions({ text: 'Corriente' });
      await escribirSaldo('');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL_CUENTAS);
      expect(peticion.request.body.saldoInicial).toBe(0);
      peticion.flush(cuenta());
      await estable();
    });

    it('un saldo negativo con Ahorro es inválido y el tipo Tarjeta de crédito lo corrige', async () => {
      await abrir({ modo: 'crear' }, 'en-US');
      await (await nombre()).setValue('Visa');
      await (await tipo()).clickOptions({ text: 'Ahorro' });
      await escribirSaldo('-100');

      expect(errores()).toContain(MENSAJE_SALDO_NEGATIVO);
      expect(await (await boton('Crear')).isDisabled()).toBe(true);

      await (await tipo()).clickOptions({ text: 'Tarjeta de crédito' });
      await estable();

      expect(errores()).not.toContain(MENSAJE_SALDO_NEGATIVO);
      expect(await (await boton('Crear')).isDisabled()).toBe(false);
      await (await boton('Crear')).click();
      const peticion = backend.expectOne(URL_CUENTAS);
      expect(peticion.request.body.saldoInicial).toBe(-100000);
      peticion.flush(cuenta());
      await estable();
    });

    it('un saldo negativo con Préstamo es válido', async () => {
      await abrir({ modo: 'crear' });
      await (await nombre()).setValue('Hipoteca');
      await (await tipo()).clickOptions({ text: 'Préstamo' });
      await escribirSaldo('-1.000');

      expect(errores()).toEqual([]);
      expect(await (await boton('Crear')).isDisabled()).toBe(false);
    });

    it.each([
      ['12.3456', 'en-US', 'Usa como máximo 3 decimales'],
      ['abc', 'es-BO', 'Escribe un monto válido'],
      ['12.5', 'es-BO', 'Escribe un monto válido'],
    ])('el saldo %s en %s muestra "%s"', async (texto, region, mensaje) => {
      await abrir({ modo: 'crear' }, region);
      await (await nombre()).setValue('Banco');
      await (await tipo()).clickOptions({ text: 'Corriente' });
      await escribirSaldo(texto);

      expect(errores()).toEqual([mensaje]);
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('la ayuda del saldo muestra un ejemplo en el formato de la región', async () => {
      await abrir({ modo: 'crear' }, 'es-BO');

      expect(textoDialogo()).toContain('Ej.: 1234,5');
    });

    it('un nombre de solo espacios muestra que es obligatorio', async () => {
      await abrir({ modo: 'crear' });
      const entrada = await nombre();
      await entrada.setValue('   ');
      await entrada.blur();

      expect(errores()).toContain('El nombre es obligatorio');
    });

    it('un nombre de 101 caracteres muestra el máximo', async () => {
      await abrir({ modo: 'crear' });
      const entrada = await nombre();
      await entrada.setValue('a'.repeat(101));
      await entrada.blur();

      expect(errores()).toContain('El nombre no puede superar los 100 caracteres');
    });

    it('deshabilita el botón mientras la respuesta está pendiente', async () => {
      await abrir({ modo: 'crear' });
      await (await nombre()).setValue('Banco');
      await (await tipo()).clickOptions({ text: 'Corriente' });
      await (await boton('Crear')).click();

      expect(await (await boton('Crear')).isDisabled()).toBe(true);
      backend.expectOne(URL_CUENTAS).flush(cuenta());
      await estable();
    });

    describe('errores de la API', () => {
      beforeEach(async () => {
        await abrir({ modo: 'crear' });
        await (await nombre()).setValue('banco');
        await (await tipo()).clickOptions({ text: 'Corriente' });
        await (await boton('Crear')).click();
      });

      it('409 CUENTA_YA_EXISTE se muestra en el nombre y el diálogo sigue abierto', async () => {
        backend
          .expectOne(URL_CUENTAS)
          .flush({ codigo: 'CUENTA_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
        await estable();

        expect(errores()).toContain('Ya tienes una cuenta con ese nombre');
        expect(resultado).toBe('abierto');
        expect(await (await nombre()).getValue()).toBe('banco');
        expect(abrirAviso).not.toHaveBeenCalled();
      });

      it('400 DATOS_INVALIDOS muestra el mensaje en el saldo inicial', async () => {
        backend
          .expectOne(URL_CUENTAS)
          .flush(
            { codigo: 'DATOS_INVALIDOS', errores: { saldoInicial: 'Saldo inválido' } },
            { status: 400, statusText: 'Bad Request' },
          );
        await estable();

        expect(errores()).toEqual(['Saldo inválido']);
        expect(resultado).toBe('abierto');
      });

      it('422 REGLA_NEGOCIO_VIOLADA se muestra como mensaje del diálogo', async () => {
        backend
          .expectOne(URL_CUENTAS)
          .flush(
            { codigo: 'REGLA_NEGOCIO_VIOLADA', detail: 'texto interno' },
            { status: 422, statusText: 'Unprocessable Entity' },
          );
        await estable();

        const alerta = document.querySelector('mat-dialog-container [role="alert"]');
        expect(alerta?.textContent?.trim()).toBe(MENSAJE_SALDO_NEGATIVO);
        expect(textoDialogo()).not.toContain('texto interno');
        expect(resultado).toBe('abierto');
        expect(abrirAviso).not.toHaveBeenCalled();
      });

      it('500 muestra el aviso genérico y deja el diálogo abierto', async () => {
        backend
          .expectOne(URL_CUENTAS)
          .flush({}, { status: 500, statusText: 'Internal Server Error' });
        await estable();

        expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
          duration: 6000,
        });
        expect(resultado).toBe('abierto');
        expect(await (await boton('Crear')).isDisabled()).toBe(false);
      });
    });

    it('Cancelar cierra sin resultado ni petición', async () => {
      await abrir({ modo: 'crear' });
      await (await boton('Cancelar')).click();
      await estable();

      expect(resultado).toBeUndefined();
    });
  });

  describe('editar', () => {
    it('muestra el nombre y el tipo actuales sin casilla ni saldo inicial', async () => {
      await abrir({ modo: 'editar', cuenta: cuenta() });

      expect(textoDialogo()).toContain('Editar cuenta');
      expect(await (await nombre()).getValue()).toBe('Banco');
      expect(await (await tipo()).getValueText()).toBe('Corriente');
      expect(await cargador.getAllHarnesses(MatCheckboxHarness)).toHaveLength(0);
      expect(await cargador.getAllHarnesses(MatInputHarness)).toHaveLength(1);
      expect(textoDialogo()).not.toContain('Saldo inicial');
    });

    it('guardar hace PUT solo con nombre y tipo', async () => {
      await abrir({ modo: 'editar', cuenta: cuenta() });
      await (await nombre()).setValue(' Ahorros ');
      await (await tipo()).clickOptions({ text: 'Ahorro' });
      await (await boton('Guardar')).click();

      const peticion = backend.expectOne(`${URL_CUENTAS}/5`);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({ nombre: 'Ahorros', tipo: 'AHORRO' });
      peticion.flush(cuenta({ nombre: 'Ahorros', tipo: 'AHORRO' }));
      await estable();

      expect(resultado).toEqual(cuenta({ nombre: 'Ahorros', tipo: 'AHORRO' }));
    });

    it('con saldo inicial negativo, un tipo que no lo admite marca el tipo', async () => {
      await abrir({
        modo: 'editar',
        cuenta: cuenta({ tipo: 'TARJETA_CREDITO', saldoInicial: -250000 }),
      });
      await (await tipo()).clickOptions({ text: 'Ahorro' });
      await estable();

      expect(errores()).toContain(
        'Esta cuenta tiene saldo inicial negativo: solo puede ser tarjeta de crédito o préstamo',
      );
      expect(await (await boton('Guardar')).isDisabled()).toBe(true);

      await (await tipo()).clickOptions({ text: 'Préstamo' });
      await estable();
      expect(await (await boton('Guardar')).isDisabled()).toBe(false);
    });

    it('422 al guardar se muestra como mensaje del diálogo', async () => {
      await abrir({ modo: 'editar', cuenta: cuenta() });
      await (await boton('Guardar')).click();
      backend
        .expectOne(`${URL_CUENTAS}/5`)
        .flush(
          { codigo: 'REGLA_NEGOCIO_VIOLADA' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_SALDO_NEGATIVO);
      expect(resultado).toBe('abierto');
    });
  });
});
