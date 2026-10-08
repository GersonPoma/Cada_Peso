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
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { DatosDialogoMoverDinero } from '../models/datos-dialogo-mover-dinero.model';
import { MesPresupuestoResponse } from '../models/mes-presupuesto-response.model';
import { ResultadoMoverDinero } from '../models/resultado-mover-dinero.model';
import {
  DialogoMoverDineroComponent,
  MENSAJE_CATEGORIA_INEXISTENTE,
  MENSAJE_SIN_DISPONIBLE,
} from './dialogo-mover-dinero.component';

const URL_MOVER = '/api/v1/presupuestos/3/meses/2026-10/mover-dinero';

function categoria(
  categoriaId: number,
  nombre: string,
  disponible: number,
  extra: Partial<CategoriaMesResponse> = {},
): CategoriaMesResponse {
  return {
    categoriaId,
    nombre,
    oculta: false,
    asignado: 0,
    actividad: 0,
    disponible,
    sobregastada: disponible < 0,
    esPagoTarjeta: false,
    cuentaId: null,
    ...extra,
  };
}

const MES: MesPresupuestoResponse = {
  mes: '2026-10',
  listoParaAsignar: 0,
  totalAsignado: 0,
  totalActividad: 0,
  totalDisponible: 0,
  grupos: [
    {
      id: 1,
      nombre: 'Necesidades',
      orden: 0,
      oculto: false,
      categorias: [
        categoria(5, 'Comida', 70000),
        categoria(6, 'Ocio', 0),
        categoria(9, 'Vieja', 1000, { oculta: true }),
      ],
    },
    {
      id: 2,
      nombre: 'Deseos',
      orden: 1,
      oculto: false,
      categorias: [categoria(8, 'Ropa', -25000)],
    },
    { id: 3, nombre: 'Oculto', orden: 2, oculto: true, categorias: [categoria(10, 'Nada', 0)] },
  ],
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoMoverDineroComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: ResultadoMoverDinero | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function abrir(datos: Omit<DatosDialogoMoverDinero, 'mes'>): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoMoverDineroComponent,
      DatosDialogoMoverDinero,
      ResultadoMoverDinero
    >(DialogoMoverDineroComponent, { data: { mes: MES, ...datos } });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function campo<T extends MatSelectHarness | MatInputHarness>(
    etiqueta: string,
    tipo: typeof MatSelectHarness | typeof MatInputHarness,
  ): Promise<T> {
    const formField = await cargador.getHarness(
      MatFormFieldHarness.with({ floatingLabelText: etiqueta }),
    );
    return (await formField.getControl(tipo as typeof MatSelectHarness)) as unknown as T;
  }

  const origen = () => campo<MatSelectHarness>('Desde', MatSelectHarness);
  const destino = () => campo<MatSelectHarness>('Hacia', MatSelectHarness);
  const monto = () => campo<MatInputHarness>('Monto', MatInputHarness);
  const mover = () => cargador.getHarness(MatButtonHarness.with({ text: 'Mover' }));
  const textoDialogo = () =>
    (document.querySelector('mat-dialog-container')?.textContent ?? '').replace(/ /g, ' ');

  async function escribirMonto(texto: string): Promise<void> {
    const entrada = await monto();
    await entrada.setValue(texto);
    await entrada.blur();
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

  it('preselecciona el origen, muestra su disponible y solo ofrece categorías visibles', async () => {
    await abrir({ origenId: 5 });

    expect(await (await origen()).getValueText()).toBe('Comida');
    expect(textoDialogo()).toContain('Disponible: Bs 70,00');
    const selector = await destino();
    await selector.open();
    const opciones = await Promise.all((await selector.getOptions()).map((o) => o.getText()));
    expect(opciones).toEqual(['Comida', 'Ocio', 'Ropa']);
  });

  it('envía origen, destino y monto en milésimas y se cierra con el mes devuelto', async () => {
    await abrir({ origenId: 5 });
    await (await destino()).clickOptions({ text: 'Ocio' });
    await escribirMonto('30');
    await (await mover()).click();

    const peticion = backend.expectOne(URL_MOVER);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ origenId: 5, destinoId: 6, monto: 30000 });
    peticion.flush({ ...MES, listoParaAsignar: 1 });
    await estable();

    expect(resultado).toEqual({ tipo: 'movido', mes: { ...MES, listoParaAsignar: 1 } });
  });

  it('cubrir sobregasto abre con el destino y el monto propuestos', async () => {
    await abrir({ destinoId: 8, monto: 25000 });

    expect(await (await destino()).getValueText()).toBe('Ropa');
    expect(await (await monto()).getValue()).toBe('25');
    expect(await (await mover()).isDisabled()).toBe(true);

    await (await origen()).clickOptions({ text: 'Comida' });
    await estable();
    expect(await (await mover()).isDisabled()).toBe(false);
  });

  it('el destino igual al origen es un error', async () => {
    await abrir({ origenId: 5 });
    await (await destino()).clickOptions({ text: 'Comida' });
    await escribirMonto('10');

    expect(textoDialogo()).toContain('El destino debe ser distinto del origen');
    expect(await (await mover()).isDisabled()).toBe(true);
  });

  it.each([
    ['0', 'El monto debe ser mayor que 0'],
    ['-5', 'El monto debe ser mayor que 0'],
    ['70,001', 'Supera el disponible del origen'],
    ['abc', 'Escribe un monto válido'],
  ])('el monto %s muestra "%s"', async (texto, mensaje) => {
    await abrir({ origenId: 5 });
    await (await destino()).clickOptions({ text: 'Ocio' });
    await escribirMonto(texto);

    expect(textoDialogo()).toContain(mensaje);
    expect(await (await mover()).isDisabled()).toBe(true);
  });

  it('mover todo el disponible es válido', async () => {
    await abrir({ origenId: 5 });
    await (await destino()).clickOptions({ text: 'Ocio' });
    await escribirMonto('70');

    expect(await (await mover()).isDisabled()).toBe(false);
  });

  it('cambiar el origen reevalúa el monto', async () => {
    await abrir({ origenId: 6 });
    await (await destino()).clickOptions({ text: 'Ropa' });
    await escribirMonto('50');
    expect(textoDialogo()).toContain('Supera el disponible del origen');

    await (await origen()).clickOptions({ text: 'Comida' });
    await estable();
    expect(textoDialogo()).not.toContain('Supera el disponible del origen');
  });

  describe('errores de la API', () => {
    beforeEach(async () => {
      await abrir({ origenId: 5 });
      await (await destino()).clickOptions({ text: 'Ocio' });
      await escribirMonto('30');
      await (await mover()).click();
    });

    it('422 se muestra en el diálogo, que sigue abierto', async () => {
      backend
        .expectOne(URL_MOVER)
        .flush(
          { codigo: 'REGLA_NEGOCIO_VIOLADA' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );
      await estable();

      expect(document.querySelector('mat-dialog-container [role="alert"]')?.textContent).toBe(
        MENSAJE_SIN_DISPONIBLE,
      );
      expect(resultado).toBe('abierto');
    });

    it('400 muestra los errores en sus campos', async () => {
      backend
        .expectOne(URL_MOVER)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { monto: 'Debe ser positivo' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await estable();

      expect(textoDialogo()).toContain('Debe ser positivo');
      expect(resultado).toBe('abierto');
    });

    it('404 avisa y cierra pidiendo recargar el mes', async () => {
      backend
        .expectOne(URL_MOVER)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_CATEGORIA_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(resultado).toEqual({ tipo: 'recargar' });
    });
  });
});
