import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DatosDialogoPresupuesto } from '../models/datos-dialogo-presupuesto.model';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { DialogoPresupuestoComponent } from './dialogo-presupuesto.component';

const URL_PRESUPUESTOS = '/api/v1/presupuestos';

const CASA: PresupuestoResponse = {
  id: 9,
  nombre: 'Casa',
  moneda: 'BOB',
  fechaCreacion: '2026-10-06T12:00:00Z',
  fechaActualizacion: '2026-10-06T12:00:00Z',
};

/** Componente vacío: solo da un fixture para cargar los arneses del overlay del diálogo. */
@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoPresupuestoComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: PresupuestoResponse | undefined | 'abierto';

  async function abrir(datos: DatosDialogoPresupuesto): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoPresupuestoComponent,
      DatosDialogoPresupuesto,
      PresupuestoResponse
    >(DialogoPresupuestoComponent, { data: datos });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const textoDialogo = () => document.querySelector('mat-dialog-container')?.textContent ?? '';
  const nombre = () => cargador.getHarness(MatInputHarness);
  const boton = (texto: string) => cargador.getHarness(MatButtonHarness.with({ text: texto }));

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
    backend = TestBed.inject(HttpTestingController);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
  });

  afterEach(() => {
    backend.verify();
    TestBed.inject(MatDialog).closeAll();
    vi.restoreAllMocks();
  });

  describe('crear', () => {
    beforeEach(() => abrir({ modo: 'crear' }));

    it('muestra el título, el nombre vacío y la moneda del perfil por defecto', async () => {
      const moneda = await cargador.getHarness(MatSelectHarness);

      expect(textoDialogo()).toContain('Nuevo presupuesto');
      expect(await (await nombre()).getValue()).toBe('');
      expect(await moneda.getValueText()).toBe('Moneda de mi perfil');
    });

    it('ofrece la moneda del perfil y las siete monedas', async () => {
      const moneda = await cargador.getHarness(MatSelectHarness);
      await moneda.open();
      const opciones = await Promise.all((await moneda.getOptions()).map((o) => o.getText()));

      expect(opciones).toEqual([
        'Moneda de mi perfil',
        'BOB',
        'USD',
        'EUR',
        'ARS',
        'BRL',
        'CLP',
        'PEN',
      ]);
    });

    it('sin tocar la moneda envía el nombre recortado y sin moneda', async () => {
      await (await nombre()).setValue('  Casa ');
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL_PRESUPUESTOS);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ nombre: 'Casa' });
      peticion.flush(CASA);
      await estable();
    });

    it('con una moneda elegida la envía', async () => {
      await (await nombre()).setValue('Viajes');
      await (await cargador.getHarness(MatSelectHarness)).clickOptions({ text: 'USD' });
      await (await boton('Crear')).click();

      const peticion = backend.expectOne(URL_PRESUPUESTOS);
      expect(peticion.request.body).toEqual({ nombre: 'Viajes', moneda: 'USD' });
      peticion.flush({ ...CASA, nombre: 'Viajes', moneda: 'USD' });
      await estable();
    });

    it('al crearse se cierra con el presupuesto como resultado', async () => {
      await (await nombre()).setValue('Casa');
      await (await boton('Crear')).click();
      backend.expectOne(URL_PRESUPUESTOS).flush(CASA);
      await estable();

      expect(resultado).toEqual(CASA);
    });

    it('un nombre de solo espacios muestra que es obligatorio y deshabilita el botón', async () => {
      const campo = await nombre();
      await campo.setValue('   ');
      await campo.blur();

      expect(textoDialogo()).toContain('El nombre es obligatorio');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('un nombre de 101 caracteres muestra el máximo y deshabilita el botón', async () => {
      const campo = await nombre();
      await campo.setValue('a'.repeat(101));
      await campo.blur();

      expect(textoDialogo()).toContain('El nombre no puede superar los 100 caracteres');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('acepta 100 caracteres sin contar los espacios de los extremos', async () => {
      await (await nombre()).setValue(`  ${'a'.repeat(100)}  `);

      expect(await (await boton('Crear')).isDisabled()).toBe(false);
    });

    it('deshabilita el botón mientras la respuesta está pendiente', async () => {
      await (await nombre()).setValue('Casa');
      await (await boton('Crear')).click();

      expect(await (await boton('Crear')).isDisabled()).toBe(true);
      backend.expectOne(URL_PRESUPUESTOS).flush(CASA);
      await estable();
    });

    it('un 409 PRESUPUESTO_YA_EXISTE marca el nombre y deja el diálogo abierto', async () => {
      await (await nombre()).setValue('casa');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL_PRESUPUESTOS)
        .flush({ codigo: 'PRESUPUESTO_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
      await estable();

      expect(textoDialogo()).toContain('Ya tienes un presupuesto con ese nombre');
      expect(resultado).toBe('abierto');
      expect(await (await nombre()).getValue()).toBe('casa');
      expect(abrirAviso).not.toHaveBeenCalled();
    });

    it('un 400 DATOS_INVALIDOS muestra el mensaje en el campo moneda', async () => {
      await (await nombre()).setValue('Casa');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL_PRESUPUESTOS)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { moneda: 'Moneda no válida' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await estable();

      const error = document.querySelector('mat-dialog-container mat-error');
      expect(error?.textContent?.trim()).toBe('Moneda no válida');
      expect(resultado).toBe('abierto');
      expect(abrirAviso).not.toHaveBeenCalled();
    });

    it('un 500 muestra el aviso genérico y deja el diálogo abierto', async () => {
      await (await nombre()).setValue('Casa');
      await (await boton('Crear')).click();
      backend
        .expectOne(URL_PRESUPUESTOS)
        .flush({ detail: 'interno' }, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      expect(textoDialogo()).not.toContain('interno');
      expect(resultado).toBe('abierto');
      expect(await (await boton('Crear')).isDisabled()).toBe(false);
    });

    it('Cancelar cierra sin resultado ni petición', async () => {
      await (await boton('Cancelar')).click();
      await estable();

      expect(resultado).toBeUndefined();
    });
  });

  describe('renombrar', () => {
    beforeEach(() => abrir({ modo: 'renombrar', presupuesto: { id: 3, nombre: 'Casa' } }));

    it('muestra el nombre actual y no tiene campo de moneda', async () => {
      expect(textoDialogo()).toContain('Renombrar presupuesto');
      expect(await (await nombre()).getValue()).toBe('Casa');
      expect(await cargador.getAllHarnesses(MatSelectHarness)).toHaveLength(0);
    });

    it('hace PUT con el nombre nuevo y se cierra con la respuesta', async () => {
      await (await nombre()).setValue(' Hogar ');
      await (await boton('Guardar')).click();

      const peticion = backend.expectOne(`${URL_PRESUPUESTOS}/3`);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({ nombre: 'Hogar' });
      peticion.flush({ ...CASA, id: 3, nombre: 'Hogar' });
      await estable();

      expect(resultado).toEqual({ ...CASA, id: 3, nombre: 'Hogar' });
    });

    it('un 409 PRESUPUESTO_YA_EXISTE marca el nombre', async () => {
      await (await nombre()).setValue('Viajes');
      await (await boton('Guardar')).click();
      backend
        .expectOne(`${URL_PRESUPUESTOS}/3`)
        .flush({ codigo: 'PRESUPUESTO_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
      await estable();

      expect(textoDialogo()).toContain('Ya tienes un presupuesto con ese nombre');
      expect(resultado).toBe('abierto');
    });
  });
});
