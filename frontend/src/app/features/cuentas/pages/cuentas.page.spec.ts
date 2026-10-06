import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoCuentaComponent } from '../components/dialogo-cuenta.component';
import { CuentaResponse } from '../models/cuenta-response.model';
import { SaldoCuentaResponse } from '../models/saldo-cuenta-response.model';
import { CuentasPage } from './cuentas.page';

const URL_CUENTAS = '/api/v1/presupuestos/3/cuentas';
const URL_SALDOS = '/api/v1/presupuestos/3/transacciones/saldos';

function cuenta(cambios: Partial<CuentaResponse>): CuentaResponse {
  return {
    id: 1,
    nombre: 'Cuenta',
    tipo: 'CORRIENTE',
    enPresupuesto: true,
    saldoInicial: 0,
    cerrada: false,
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
    ...cambios,
  };
}

const BANCO = cuenta({ id: 5, nombre: 'Banco' });
const TARJETA = cuenta({ id: 6, nombre: 'Visa', tipo: 'TARJETA_CREDITO' });
const INVERSIONES = cuenta({
  id: 7,
  nombre: 'Inversiones',
  tipo: 'INVERSION',
  enPresupuesto: false,
});
const VIEJA = cuenta({ id: 8, nombre: 'Vieja', tipo: 'AHORRO', cerrada: true });

const SALDOS: SaldoCuentaResponse[] = [
  { cuentaId: 5, saldo: 1500000, saldoConciliado: 1000000 },
  { cuentaId: 6, saldo: -250000, saldoConciliado: 0 },
  { cuentaId: 7, saldo: 9000000, saldoConciliado: 9000000 },
  { cuentaId: 8, saldo: 0, saldoConciliado: 0 },
];

function normalizar(texto: string): string {
  return texto.replace(/[  ]/g, ' ').replace(/\s+/g, ' ');
}

describe('CuentasPage', () => {
  let fixture: ComponentFixture<CuentasPage>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let accionAviso: Subject<void>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent ?? '');
  const seccion = (titulo: string) =>
    Array.from(elemento().querySelectorAll('section')).find(
      (s) => s.querySelector('h2')?.textContent?.trim() === titulo,
    );
  const fila = (nombre: string) =>
    Array.from(elemento().querySelectorAll('mat-list-item')).find((item) =>
      item.textContent?.includes(nombre),
    ) as HTMLElement | undefined;
  const botonCon = (contenido: string) =>
    Array.from(elemento().querySelectorAll('button')).find((b) =>
      b.textContent?.includes(contenido),
    ) as HTMLButtonElement | undefined;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function peticionCuentas(incluirCerradas: boolean) {
    return backend.expectOne(
      (p) => p.url === URL_CUENTAS && p.params.get('incluirCerradas') === String(incluirCerradas),
    );
  }

  /** Responde la lista y los saldos pendientes. */
  async function responder(
    cuentas: CuentaResponse[],
    saldos: SaldoCuentaResponse[] = SALDOS,
    incluirCerradas = false,
  ): Promise<void> {
    peticionCuentas(incluirCerradas).flush(cuentas);
    backend.expectOne(URL_SALDOS).flush(saldos);
    await estable();
  }

  async function accionDe(nombre: string, accion: string): Promise<void> {
    const menu = await cargador.getHarness(
      MatMenuHarness.with({ selector: `[aria-label="Acciones de ${nombre}"]` }),
    );
    await menu.open();
    await menu.clickItem({ text: new RegExp(accion) });
    await estable();
  }

  function simularDialogo(resultado: CuentaResponse | undefined) {
    return vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(resultado),
    } as MatDialogRef<unknown, unknown>);
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'USD' });
    backend = TestBed.inject(HttpTestingController);
    accionAviso = new Subject<void>();
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockReturnValue({
      onAction: () => accionAviso.asObservable(),
    } as MatSnackBarRef<TextOnlySnackBar>);
    fixture = TestBed.createComponent(CuentasPage);
    cargador = TestbedHarnessEnvironment.loader(fixture);
    await estable();
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('mientras carga muestra el spinner', async () => {
    expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
    await responder([]);
  });

  describe('con cuentas', () => {
    beforeEach(() => responder([BANCO, INVERSIONES, TARJETA]));

    it('pide la lista sin cerradas y la agrupa en secciones', () => {
      expect(elemento().querySelector('mat-progress-spinner')).toBeNull();
      expect(seccion('En el presupuesto')?.textContent).toContain('Banco');
      expect(seccion('En el presupuesto')?.textContent).toContain('Visa');
      expect(seccion('Seguimiento')?.textContent).toContain('Inversiones');
      expect(seccion('En el presupuesto')?.textContent).not.toContain('Inversiones');
      expect(seccion('Cerradas')).toBeUndefined();
    });

    it('muestra el saldo y el conciliado de cada cuenta en la moneda del presupuesto', () => {
      const banco = normalizar(fila('Banco')?.textContent ?? '');

      expect(banco).toContain('$1,500.00');
      expect(banco).toContain('Conciliado: $1,000.00');
    });

    it('muestra el tipo en español', () => {
      expect(fila('Visa')?.textContent).toContain('Tarjeta de crédito');
      expect(fila('Inversiones')?.textContent).toContain('Inversión');
      expect(fila('Banco')?.textContent).toContain('Corriente');
    });

    it('un saldo negativo se pinta en color de error', () => {
      const saldoVisa = fila('Visa')?.querySelector('.monto');
      const saldoBanco = fila('Banco')?.querySelector('.monto');

      expect(saldoVisa?.classList.contains('negativo')).toBe(true);
      expect(normalizar(saldoVisa?.textContent ?? '')).toContain('-$250.00');
      expect(saldoBanco?.classList.contains('negativo')).toBe(false);
    });

    it('el total suma solo las cuentas abiertas del presupuesto', () => {
      expect(texto()).toContain('Total en el presupuesto: $1,250.00');
    });
  });

  it('una cuenta que no está en los saldos muestra 0', async () => {
    await responder([BANCO], []);

    const banco = normalizar(fila('Banco')?.textContent ?? '');
    expect(banco).toContain('$0.00');
    expect(banco).toContain('Conciliado: $0.00');
  });

  it('Ver cuentas cerradas vuelve a pedir la lista con incluirCerradas y muestra Cerradas', async () => {
    await responder([BANCO]);

    await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
    await estable();
    await responder([BANCO, VIEJA], SALDOS, true);

    expect(seccion('Cerradas')?.textContent).toContain('Vieja');
    expect(texto()).toContain('Total en el presupuesto: $1,500.00');

    await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
    await estable();
    await responder([BANCO], SALDOS, false);
    expect(seccion('Cerradas')).toBeUndefined();
  });

  it('sin cuentas muestra el estado vacío con un botón para agregar', async () => {
    await responder([]);

    expect(texto()).toContain('Aún no tienes cuentas');
    expect(botonCon('Agregar mi primera cuenta')).toBeDefined();
  });

  describe('error al cargar', () => {
    beforeEach(async () => {
      peticionCuentas(false).flush({}, { status: 500, statusText: 'Internal Server Error' });
      backend.expectOne(URL_SALDOS);
      await estable();
    });

    it('muestra el aviso genérico con Reintentar', () => {
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar tus cuentas.');
    });

    it('Reintentar en el aviso vuelve a pedir la lista y los saldos', async () => {
      accionAviso.next();
      await estable();
      await responder([BANCO]);

      expect(fila('Banco')).toBeDefined();
    });

    it('el botón Reintentar de la página también recarga', async () => {
      botonCon('Reintentar')?.click();
      await estable();
      await responder([BANCO]);

      expect(fila('Banco')).toBeDefined();
    });
  });

  it('un 401 no muestra ningún aviso', async () => {
    peticionCuentas(false).flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(URL_SALDOS);
    await estable();

    expect(abrirAviso).not.toHaveBeenCalled();
  });

  describe('operaciones', () => {
    beforeEach(() => responder([BANCO, VIEJA], SALDOS));

    it('Agregar cuenta abre el diálogo de crear y, al crearse, recarga', async () => {
      const abrirDialogo = simularDialogo(cuenta({ id: 9, nombre: 'Nueva' }));

      botonCon('Agregar cuenta')?.click();
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoCuentaComponent,
        expect.objectContaining({ data: { modo: 'crear' } }),
      );
      await responder([BANCO, cuenta({ id: 9, nombre: 'Nueva' })]);
      expect(fila('Nueva')).toBeDefined();
    });

    it('cancelar el diálogo no recarga', async () => {
      simularDialogo(undefined);

      botonCon('Agregar cuenta')?.click();
      await estable();

      backend.expectNone(URL_SALDOS);
    });

    it('Editar abre el diálogo con la cuenta', async () => {
      const abrirDialogo = simularDialogo(undefined);

      await accionDe('Banco', 'Editar');

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoCuentaComponent,
        expect.objectContaining({ data: { modo: 'editar', cuenta: BANCO } }),
      );
    });

    it('Cerrar llama al endpoint y recarga', async () => {
      await accionDe('Banco', 'Cerrar');

      const peticion = backend.expectOne(`${URL_CUENTAS}/5/cerrar`);
      expect(peticion.request.method).toBe('POST');
      peticion.flush({ ...BANCO, cerrada: true });
      await estable();
      await responder([]);
    });

    it('Reabrir aparece en las cerradas, llama al endpoint y recarga', async () => {
      await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
      await estable();
      await responder([BANCO, VIEJA], SALDOS, true);

      await accionDe('Vieja', 'Reabrir');

      backend.expectOne(`${URL_CUENTAS}/8/reabrir`).flush({ ...VIEJA, cerrada: false });
      await estable();
      await responder([BANCO, { ...VIEJA, cerrada: false }], SALDOS, true);
      expect(seccion('Cerradas')).toBeUndefined();
    });

    it('un 500 al cerrar muestra el aviso genérico', async () => {
      await accionDe('Banco', 'Cerrar');

      backend
        .expectOne(`${URL_CUENTAS}/5/cerrar`)
        .flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
    });
  });
});
