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
import { BeneficiarioResponse } from '../models/beneficiario-response.model';
import {
  DatosDialogoBeneficiario,
  ResultadoDialogoBeneficiario,
} from '../models/datos-dialogo-beneficiario.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import {
  DialogoBeneficiarioComponent,
  MENSAJE_BENEFICIARIO_INEXISTENTE,
  MENSAJE_NOMBRE_REPETIDO,
  PISTA_RENOMBRAR,
} from './dialogo-beneficiario.component';

const URL = '/api/v1/presupuestos/3/beneficiarios';

const GRUPOS: GrupoCategoriasLectura[] = [
  {
    id: 1,
    nombre: 'Gustos',
    oculto: false,
    categorias: [
      { id: 7, nombre: 'Ocio', oculta: false, esPagoTarjeta: false },
      { id: 8, nombre: 'Viejo', oculta: true, esPagoTarjeta: false },
      { id: 9, nombre: 'Archivo', oculta: true, esPagoTarjeta: false },
    ],
  },
  {
    id: 2,
    nombre: 'Pagos de tarjetas',
    oculto: false,
    categorias: [{ id: 20, nombre: 'Pago: Visa', oculta: false, esPagoTarjeta: true }],
  },
];

const NETFLIX: BeneficiarioResponse = { id: 4, nombre: 'Netflix', categoriaPredeterminadaId: 7 };

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoBeneficiarioComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoDialogoBeneficiario | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(beneficiario: BeneficiarioResponse | null = null): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoBeneficiarioComponent,
      DatosDialogoBeneficiario,
      ResultadoDialogoBeneficiario
    >(DialogoBeneficiarioComponent, { data: { beneficiario, grupos: GRUPOS } });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  const campo = (etiqueta: string) =>
    cargador.getHarness(MatFormFieldHarness.with({ floatingLabelText: etiqueta }));
  async function nombre(): Promise<MatInputHarness> {
    return (await (await campo('Nombre')).getControl(MatInputHarness)) as MatInputHarness;
  }
  async function categoria(): Promise<MatSelectHarness> {
    return (await (
      await campo('Categoría predeterminada')
    ).getControl(MatSelectHarness)) as MatSelectHarness;
  }
  async function escribirNombre(texto: string): Promise<void> {
    const entrada = await nombre();
    await entrada.setValue(texto);
    await entrada.blur();
    await estable();
  }
  const boton = (t: string) => cargador.getHarness(MatButtonHarness.with({ text: t }));
  const textoDialogo = () => document.querySelector('mat-dialog-container')?.textContent ?? '';

  async function guardar(texto = 'Crear') {
    await (await boton(texto)).click();
    return backend.expectOne((p) => p.url.startsWith(URL));
  }

  beforeEach(() => {
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
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('crear', () => {
    beforeEach(() => abrir());

    it('envía el nombre recortado y la categoría elegida', async () => {
      await escribirNombre('  Spotify ');
      await (await categoria()).clickOptions({ text: 'Ocio' });
      const peticion = await guardar();

      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ nombre: 'Spotify', categoriaId: 7 });
      peticion.flush({ id: 5, nombre: 'Spotify', categoriaPredeterminadaId: 7 });
      await estable();
      expect(resultado).toEqual({ tipo: 'guardado' });
    });

    it('sin categoría envía null', async () => {
      await escribirNombre('Banco');
      const peticion = await guardar();

      expect(peticion.request.body).toEqual({ nombre: 'Banco', categoriaId: null });
      peticion.flush({ id: 5, nombre: 'Banco', categoriaPredeterminadaId: null });
      await estable();
    });

    it('ofrece Ninguna y las visibles, sin ocultas ni categorías de pago', async () => {
      const selector = await categoria();
      await selector.open();

      expect(await Promise.all((await selector.getOptions()).map((o) => o.getText()))).toEqual([
        'Ninguna',
        'Ocio',
      ]);
      await selector.clickOptions({ text: 'Ninguna' });
    });

    it('un nombre de solo espacios es obligatorio y deshabilita el botón', async () => {
      await escribirNombre('   ');

      expect(textoDialogo()).toContain('El nombre es obligatorio');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('más de 100 caracteres no se permite', async () => {
      await escribirNombre('a'.repeat(101));

      expect(textoDialogo()).toContain('El nombre no puede superar los 100 caracteres');
      expect(await (await boton('Crear')).isDisabled()).toBe(true);
    });

    it('muestra el contador de caracteres', async () => {
      await (await nombre()).setValue('  Sol ');
      await estable();

      expect(textoDialogo()).toContain('3/100');
    });

    it('el botón queda deshabilitado mientras envía', async () => {
      await escribirNombre('Banco');
      const peticion = await guardar();
      await estable();

      expect(await (await boton('Crear')).isDisabled()).toBe(true);
      peticion.flush(NETFLIX);
      await estable();
    });

    it('409 muestra el nombre repetido en su campo y sigue abierto', async () => {
      await escribirNombre('netflix');
      const peticion = await guardar();
      peticion.flush({ codigo: 'BENEFICIARIO_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
      await estable();

      expect(await (await campo('Nombre')).getTextErrors()).toEqual([MENSAJE_NOMBRE_REPETIDO]);
      expect(resultado).toBe('abierto');
    });

    it('400 pone el mensaje en su campo', async () => {
      await escribirNombre('Banco');
      const peticion = await guardar();
      peticion.flush(
        { codigo: 'DATOS_INVALIDOS', errores: { nombre: 'Nombre inválido' } },
        { status: 400, statusText: 'Bad Request' },
      );
      await estable();

      expect(await (await campo('Nombre')).getTextErrors()).toEqual(['Nombre inválido']);
      expect(abrirAviso).not.toHaveBeenCalled();
      expect(resultado).toBe('abierto');
    });

    it('404 avisa y cierra pidiendo recargar', async () => {
      await escribirNombre('Banco');
      await (await categoria()).clickOptions({ text: 'Ocio' });
      const peticion = await guardar();
      peticion.flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_BENEFICIARIO_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });

    it('otro error (422) avisa de forma genérica y sigue abierto', async () => {
      await escribirNombre('Banco');
      const peticion = await guardar();
      peticion.flush(
        { codigo: 'REGLA_NEGOCIO_VIOLADA' },
        { status: 422, statusText: 'Unprocessable' },
      );
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toBe('abierto');
    });
  });

  describe('editar', () => {
    it('Ninguna quita la categoría y envía PUT', async () => {
      await abrir(NETFLIX);
      expect(await (await nombre()).getValue()).toBe('Netflix');
      expect(await (await categoria()).getValueText()).toBe('Ocio');

      await (await categoria()).clickOptions({ text: 'Ninguna' });
      const peticion = await guardar('Guardar');

      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.url).toBe(`${URL}/4`);
      expect(peticion.request.body).toEqual({ nombre: 'Netflix', categoriaId: null });
      peticion.flush({ ...NETFLIX, categoriaPredeterminadaId: null });
      await estable();
      expect(resultado).toEqual({ tipo: 'guardado' });
    });

    it('avisa al renombrar y no si el nombre queda igual', async () => {
      await abrir(NETFLIX);
      expect(textoDialogo()).not.toContain(PISTA_RENOMBRAR);

      await (await nombre()).setValue('Netflix Premium');
      await estable();
      expect(textoDialogo()).toContain(PISTA_RENOMBRAR);

      await (await nombre()).setValue(' Netflix ');
      await estable();
      expect(textoDialogo()).not.toContain(PISTA_RENOMBRAR);
    });

    it('una categoría oculta ya elegida aparece; las demás ocultas no', async () => {
      await abrir({ ...NETFLIX, categoriaPredeterminadaId: 8 });
      const selector = await categoria();
      expect(await selector.getValueText()).toBe('Viejo');

      await selector.open();
      expect(await Promise.all((await selector.getOptions()).map((o) => o.getText()))).toEqual([
        'Ninguna',
        'Ocio',
        'Viejo',
      ]);
      await selector.clickOptions({ text: 'Viejo' });
    });

    it('404 avisa y cierra pidiendo recargar', async () => {
      await abrir(NETFLIX);
      const peticion = await guardar('Guardar');
      peticion.flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(resultado).toEqual({ tipo: 'recargar' });
    });
  });
});
