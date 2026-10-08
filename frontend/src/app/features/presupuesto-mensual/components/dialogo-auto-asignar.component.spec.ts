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
import { MatSelectionListHarness } from '@angular/material/list/testing';
import { MatRadioGroupHarness } from '@angular/material/radio/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { AutoAsignarResponse } from '../models/auto-asignar.model';
import {
  DatosDialogoAutoAsignar,
  ResultadoDialogoAutoAsignar,
} from '../models/datos-dialogos-metas.model';
import {
  AYUDA_TODAS,
  DialogoAutoAsignarComponent,
  MENSAJE_AUTO_ASIGNAR_INEXISTENTE,
  MENSAJE_DATOS_AUTO_ASIGNAR,
  MENSAJE_SIN_CAMBIOS,
  MENSAJE_SIN_CATEGORIAS,
  MENSAJE_SIN_META,
} from './dialogo-auto-asignar.component';

const URL = '/api/v1/presupuestos/3/meses/2026-10/auto-asignar';

const DATOS: DatosDialogoAutoAsignar = {
  mes: '2026-10',
  categorias: [
    { categoriaId: 7, nombre: 'Comida', esPagoTarjeta: false, tieneMeta: true },
    { categoriaId: 8, nombre: 'Luz', esPagoTarjeta: false, tieneMeta: false },
    { categoriaId: 9, nombre: 'Visa', esPagoTarjeta: true, tieneMeta: false },
  ],
};

const PREVIA: AutoAsignarResponse = {
  aplicado: false,
  listoParaAsignarAntes: 100000,
  listoParaAsignarDespues: 30000,
  cambios: [{ categoriaId: 7, nombre: 'Comida', asignadoAntes: 30000, asignadoDespues: 100000 }],
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoAutoAsignarComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoDialogoAutoAsignar | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoAutoAsignarComponent,
      DatosDialogoAutoAsignar,
      ResultadoDialogoAutoAsignar
    >(DialogoAutoAsignarComponent, { data: DATOS });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  const estrategia = () => cargador.getHarness(MatSelectHarness);
  async function elegirEstrategia(texto: string): Promise<void> {
    await (await estrategia()).clickOptions({ text: texto });
    await estable();
  }
  async function alcance(texto: string): Promise<void> {
    await (await cargador.getHarness(MatRadioGroupHarness)).checkRadioButton({ label: texto });
    await estable();
  }
  async function marcar(...nombres: RegExp[]): Promise<void> {
    const lista = await cargador.getHarness(MatSelectionListHarness);
    for (const nombre of nombres) {
      await lista.selectItems({ fullText: nombre });
    }
    await estable();
  }
  const boton = (t: string) => cargador.getHarness(MatButtonHarness.with({ text: t }));
  const textoDialogo = () =>
    (document.querySelector('mat-dialog-container')?.textContent ?? '')
      .replace(/[  ]/g, ' ')
      .replace(/\s+/g, ' ');

  async function vistaPrevia(): Promise<TestRequest> {
    await (await boton('Vista previa')).click();
    await estable();
    return backend.expectOne(URL);
  }

  async function mostrarPrevia(respuesta: AutoAsignarResponse = PREVIA): Promise<void> {
    (await vistaPrevia()).flush(respuesta);
    await estable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'USD' });
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

  it('ofrece las cinco estrategias y sin elegir una no hay vista previa', async () => {
    await abrir();
    const select = await estrategia();
    await select.open();
    const textos = await Promise.all((await select.getOptions()).map((o) => o.getText()));
    (document.querySelector('.cdk-overlay-transparent-backdrop') as HTMLElement | null)?.click();
    await estable();

    expect(textos).toEqual([
      'Lo que falta para las metas',
      'Lo asignado el mes pasado',
      'Lo gastado el mes pasado',
      'Promedio asignado (3 meses)',
      'Promedio gastado (3 meses)',
    ]);
    expect(await (await boton('Vista previa')).isDisabled()).toBe(true);
    expect(await (await boton('Aplicar')).isDisabled()).toBe(true);
  });

  it('con todas las categorías explica que no incluye las de pago de tarjeta', async () => {
    await abrir();

    expect(textoDialogo()).toContain(AYUDA_TODAS);
  });

  it('simula sin categoriaIds, muestra los cambios y aplica con simular false', async () => {
    await abrir();
    await elegirEstrategia('Lo que falta para las metas');
    const peticion = await vistaPrevia();

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ estrategia: 'FALTANTE_META', simular: true });
    peticion.flush(PREVIA);
    await estable();

    expect(textoDialogo()).toContain('Comida $30.00 → $100.00');
    expect(textoDialogo()).toContain('Listo para asignar antes $100.00');
    expect(textoDialogo()).toContain('Listo para asignar después $30.00');
    expect(resultado).toBe('abierto');

    await (await boton('Aplicar')).click();
    await estable();
    const aplicar = backend.expectOne(URL);
    expect(aplicar.request.body).toEqual({ estrategia: 'FALTANTE_META', simular: false });
    aplicar.flush({
      ...PREVIA,
      aplicado: true,
      cambios: [...PREVIA.cambios, { ...PREVIA.cambios[0], categoriaId: 8, nombre: 'Luz' }],
    });
    await estable();

    expect(resultado).toEqual({ tipo: 'aplicado', cambios: 2 });
  });

  it('con categorías elegidas envía sus ids, incluida la de pago de tarjeta', async () => {
    await abrir();
    await elegirEstrategia('Lo asignado el mes pasado');
    await alcance('Elegir categorías');
    const lista = await cargador.getHarness(MatSelectionListHarness);
    const textos = await Promise.all((await lista.getItems()).map((i) => i.getFullText()));

    expect(textos.map((t) => t.trim())).toEqual(['Comida', 'Luz', 'Visa (pago de tarjeta)']);
    await marcar(/^Comida/, /^Visa/);
    const peticion = await vistaPrevia();

    expect(peticion.request.body).toEqual({
      estrategia: 'ASIGNADO_MES_PASADO',
      categoriaIds: [7, 9],
      simular: true,
    });
    peticion.flush(PREVIA);
    await estable();
  });

  it('con Elegir categorías y ninguna marcada no hay vista previa', async () => {
    await abrir();
    await elegirEstrategia('Lo gastado el mes pasado');
    await alcance('Elegir categorías');

    expect(await (await boton('Vista previa')).isDisabled()).toBe(true);
    expect(textoDialogo()).toContain(MENSAJE_SIN_CATEGORIAS);

    await marcar(/^Luz/);
    expect(await (await boton('Vista previa')).isDisabled()).toBe(false);
    expect(textoDialogo()).not.toContain(MENSAJE_SIN_CATEGORIAS);
  });

  it('sin cambios lo dice y Aplicar queda deshabilitado', async () => {
    await abrir();
    await elegirEstrategia('Promedio asignado (3 meses)');
    await mostrarPrevia({ ...PREVIA, listoParaAsignarDespues: 100000, cambios: [] });

    expect(textoDialogo()).toContain(MENSAJE_SIN_CAMBIOS);
    expect(await (await boton('Aplicar')).isDisabled()).toBe(true);
  });

  it('un listo para asignar negativo se marca con texto', async () => {
    await abrir();
    await elegirEstrategia('Promedio gastado (3 meses)');
    await mostrarPrevia({ ...PREVIA, listoParaAsignarDespues: -20000 });
    const despues = document.querySelector('.listo-despues') as HTMLElement;

    expect(despues.classList.contains('negativo')).toBe(true);
    expect(despues.textContent).toContain('(asignaste de más)');
    expect(document.querySelector('.listo-antes')?.classList.contains('negativo')).toBe(false);
  });

  it('con Lo que falta para las metas avisa si hay categorías sin meta', async () => {
    await abrir();
    await elegirEstrategia('Lo que falta para las metas');
    await alcance('Elegir categorías');
    await marcar(/^Comida/);
    expect(textoDialogo()).not.toContain(MENSAJE_SIN_META);

    await marcar(/^Luz/);
    expect(textoDialogo()).toContain(MENSAJE_SIN_META);

    await elegirEstrategia('Lo asignado el mes pasado');
    expect(textoDialogo()).not.toContain(MENSAJE_SIN_META);
  });

  it('cambiar la estrategia descarta la vista previa', async () => {
    await abrir();
    await elegirEstrategia('Lo que falta para las metas');
    await mostrarPrevia();
    expect(await (await boton('Aplicar')).isDisabled()).toBe(false);

    await elegirEstrategia('Lo gastado el mes pasado');

    expect(document.querySelector('.previa')).toBeNull();
    expect(await (await boton('Aplicar')).isDisabled()).toBe(true);
  });

  it('cambiar el alcance descarta la vista previa', async () => {
    await abrir();
    await elegirEstrategia('Lo que falta para las metas');
    await mostrarPrevia();

    await alcance('Elegir categorías');

    expect(document.querySelector('.previa')).toBeNull();
    expect(await (await boton('Aplicar')).isDisabled()).toBe(true);
  });

  it('mientras envía, los botones están deshabilitados', async () => {
    await abrir();
    await elegirEstrategia('Lo que falta para las metas');
    const peticion = await vistaPrevia();

    expect(await (await boton('Vista previa')).isDisabled()).toBe(true);
    expect(await (await boton('Cancelar')).isDisabled()).toBe(true);
    peticion.flush(PREVIA);
    await estable();
    expect(await (await boton('Vista previa')).isDisabled()).toBe(false);
  });

  describe('errores', () => {
    beforeEach(async () => {
      await abrir();
      await elegirEstrategia('Lo que falta para las metas');
    });

    it('400 por la lista de categorías muestra Elige al menos una categoría', async () => {
      (await vistaPrevia()).flush(
        { codigo: 'DATOS_INVALIDOS', errores: { categoriaIds: 'size must be ≥ 1' } },
        { status: 400, statusText: 'Bad Request' },
      );
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_SIN_CATEGORIAS);
      expect(resultado).toBe('abierto');
    });

    it('otro 400 se muestra en el diálogo', async () => {
      (await vistaPrevia()).flush(
        { codigo: 'DATOS_INVALIDOS' },
        { status: 400, statusText: 'Bad Request' },
      );
      await estable();

      expect(textoDialogo()).toContain(MENSAJE_DATOS_AUTO_ASIGNAR);
      expect(resultado).toBe('abierto');
    });

    it('404 avisa y cierra pidiendo recargar', async () => {
      (await vistaPrevia()).flush(
        { codigo: 'RECURSO_NO_ENCONTRADO' },
        { status: 404, statusText: 'Not Found' },
      );
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_AUTO_ASIGNAR_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });

    it('404 al aplicar también cierra pidiendo recargar', async () => {
      await mostrarPrevia();
      await (await boton('Aplicar')).click();
      await estable();
      backend
        .expectOne(URL)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(resultado).toEqual({ tipo: 'recargar' });
    });

    it('otro error avisa de forma genérica y sigue abierto', async () => {
      (await vistaPrevia()).flush(null, { status: 500, statusText: 'Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toBe('abierto');
    });
  });
});
