import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoConfirmarConciliacionComponent } from '../components/dialogo-confirmar-conciliacion.component';
import { ConciliacionResponse } from '../models/conciliacion-response.model';
import { CuentaConciliacion } from '../models/cuenta-conciliacion.model';
import { EstadoConciliacionResponse } from '../models/estado-conciliacion-response.model';
import { TransaccionNoConciliada } from '../models/transaccion-no-conciliada.model';
import {
  ConciliacionPage,
  ESPERA_ESTADO_MS,
  MENSAJE_CUENTA_INEXISTENTE,
  MENSAJE_DATOS_EXTRACTO,
  MENSAJE_REFERENCIA_INEXISTENTE,
  MENSAJE_REGLA_CONCILIACION,
  MENSAJE_SIN_AJUSTE,
} from './conciliacion.page';

const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';
const BASE = '/api/v1/presupuestos/3';
const URL_CONCILIACION = `${BASE}/cuentas/5/conciliacion`;

function cuenta(cambios: Partial<CuentaConciliacion> = {}): CuentaConciliacion {
  return {
    id: 5,
    nombre: 'Banco',
    tipo: 'CORRIENTE',
    enPresupuesto: true,
    cerrada: false,
    ...cambios,
  };
}

const GRUPOS = [
  {
    id: 1,
    nombre: 'Gastos',
    oculto: false,
    categorias: [
      { id: 7, nombre: 'Comisiones', oculta: false, esPagoTarjeta: false },
      { id: 8, nombre: 'Comida', oculta: false, esPagoTarjeta: false },
      { id: 9, nombre: 'Escondida', oculta: true, esPagoTarjeta: false },
    ],
  },
  {
    id: 2,
    nombre: 'Pagos de tarjeta',
    oculto: false,
    categorias: [{ id: 20, nombre: 'Pago: Visa', oculta: false, esPagoTarjeta: true }],
  },
  {
    id: 3,
    nombre: 'Archivo',
    oculto: true,
    categorias: [{ id: 30, nombre: 'Vieja', oculta: false, esPagoTarjeta: false }],
  },
];

function conciliacion(cambios: Partial<ConciliacionResponse> = {}): ConciliacionResponse {
  return {
    id: 1,
    cuentaId: 5,
    fecha: '2026-09-30',
    saldoExtracto: 150000,
    ajuste: 0,
    transaccionAjusteId: null,
    cantidadReconciliadas: 4,
    fechaCreacion: '2026-10-01T12:00:00Z',
    ...cambios,
  };
}

function pendiente(id: number, fecha: string): TransaccionNoConciliada {
  return { id, cuentaId: 5, fecha, monto: -1000, beneficiario: null, estado: 'NO_CONCILIADA' };
}

function estadoCon(
  peticion: TestRequest,
  diferencia: number,
  cambios: Partial<EstadoConciliacionResponse> = {},
): EstadoConciliacionResponse {
  const saldoExtracto = Number(peticion.request.params.get('saldoExtracto'));
  return {
    cuentaId: 5,
    fecha: peticion.request.params.get('fecha') as string,
    saldoExtracto,
    saldoConciliado: saldoExtracto - diferencia,
    saldoConciliadoAlCorte: saldoExtracto - diferencia,
    diferencia,
    totalNoConciliadas: 0,
    noConciliadas: [],
    ...cambios,
  };
}

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

@Component({ template: 'Pantalla de cuentas' })
class CuentasDePrueba {}

@Component({ template: 'Pantalla de transacciones' })
class TransaccionesDePrueba {}

// Cada caso recorre la pantalla con harnesses y esperas: con la suite completa en paralelo,
// los 5 s por defecto no alcanzan.
describe('ConciliacionPage', { timeout: 30000 }, () => {
  let harness: RouterTestingHarness;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let abrirDialogo: ReturnType<typeof vi.spyOn>;
  let confirmar: boolean;

  const elemento = () => harness.routeNativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);
  const url = () => TestBed.inject(Router).url;
  const boton = (t: string) =>
    Array.from(elemento().querySelectorAll('button')).find(
      (b) => normalizar(b.textContent) === t,
    ) as HTMLButtonElement | undefined;
  const reconciliarBoton = () => boton('Reconciliar') as HTMLButtonElement;
  const entradaSaldo = () =>
    elemento().querySelector('app-campo-monto input') as HTMLInputElement | null;
  const entradaFecha = () =>
    elemento().querySelector('input[formcontrolname="fecha"]') as HTMLInputElement | null;
  const casilla = () =>
    elemento().querySelector('.crear-ajuste input[type="checkbox"]') as HTMLInputElement | null;
  const campoCategoria = () =>
    elemento().querySelector('mat-select[formcontrolname="categoriaId"]');
  const peticionesEstado = () => backend.match((p) => p.url === `${URL_CONCILIACION}/estado`);

  async function estable(): Promise<void> {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
  }

  async function esperar(ms = ESPERA_ESTADO_MS): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    await estable();
  }

  /** Escribe el saldo y lo confirma como al salir del campo. */
  async function saldo(valor: string): Promise<void> {
    const entrada = entradaSaldo() as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    await estable();
  }

  async function fecha(valor: string): Promise<void> {
    const entrada = entradaFecha() as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    await estable();
  }

  /** Espera la consulta del estado y la responde con la diferencia indicada. */
  async function responderEstado(
    diferencia: number,
    cambios: Partial<EstadoConciliacionResponse> = {},
  ): Promise<TestRequest> {
    await esperar();
    const peticion = backend.expectOne((p) => p.url === `${URL_CONCILIACION}/estado`);
    peticion.flush(estadoCon(peticion, diferencia, cambios));
    await estable();
    return peticion;
  }

  function responderCarga(
    datos: CuentaConciliacion = cuenta(),
    historial: ConciliacionResponse[] = [],
  ): void {
    backend.expectOne(`${BASE}/cuentas/5`).flush(datos);
    backend.expectOne((p) => p.url === `${BASE}/categorias`).flush(GRUPOS);
    backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET').flush(historial);
  }

  async function entrar(
    datos: CuentaConciliacion = cuenta(),
    historial: ConciliacionResponse[] = [],
  ): Promise<void> {
    await harness.navigateByUrl('/presupuestos/3/cuentas/5/conciliacion');
    responderCarga(datos, historial);
    await estable();
  }

  /** Entra, escribe el saldo y responde el estado con la diferencia. */
  async function conDiferencia(
    diferencia: number,
    datos: CuentaConciliacion = cuenta(),
  ): Promise<void> {
    await entrar(datos);
    await saldo('150');
    await responderEstado(diferencia);
  }

  async function marcarAjuste(): Promise<void> {
    casilla()?.click();
    await estable();
  }

  async function categoria(): Promise<MatSelectHarness> {
    return cargador.getHarness(
      MatSelectHarness.with({ selector: '[formcontrolname="categoriaId"]' }),
    );
  }

  async function reconciliar(): Promise<TestRequest> {
    reconciliarBoton().click();
    await estable();
    return backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'POST');
  }

  beforeEach(async () => {
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date(2026, 9, 8, 12));
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'presupuestos/:presupuestoId/cuentas', component: CuentasDePrueba },
          {
            path: 'presupuestos/:presupuestoId/cuentas/:cuentaId/conciliacion',
            component: ConciliacionPage,
          },
          { path: 'presupuestos/:presupuestoId/transacciones', component: TransaccionesDePrueba },
        ]),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'USD' });
    backend = TestBed.inject(HttpTestingController);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    confirmar = true;
    abrirDialogo = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockImplementation(
        () => ({ afterClosed: () => of(confirmar) }) as MatDialogRef<unknown, unknown>,
      );
    harness = await RouterTestingHarness.create();
    cargador = TestbedHarnessEnvironment.documentRootLoader(harness.fixture);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
    try {
      // Las peticiones canceladas (forkJoin que falla, consulta reemplazada) no se responden.
      backend.verify({ ignoreCancelled: true });
    } finally {
      // Si verify falla, el módulo igual se reinicia: el siguiente caso no hereda el fallo.
      TestBed.resetTestingModule();
    }
  });

  describe('carga', () => {
    it('pide cuenta, categorías e historial; muestra nombre, tipo y filas', async () => {
      await entrar(cuenta(), [conciliacion({ id: 2, ajuste: -5000 }), conciliacion({ id: 1 })]);

      expect(texto()).toContain('Conciliar Banco');
      expect(texto()).toContain('Corriente');
      expect(elemento().querySelectorAll('li.conciliacion')).toHaveLength(2);
      const volver = elemento().querySelector('a.volver') as HTMLAnchorElement;
      expect(volver.getAttribute('href')).toBe('/presupuestos/3/cuentas');
    });

    it('una cuenta inexistente avisa y lleva a Cuentas', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/conciliacion');
      backend
        .expectOne(`${BASE}/cuentas/5`)
        .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_CUENTA_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      expect(url()).toBe('/presupuestos/3/cuentas');
    });

    it('otro error muestra Reintentar, que vuelve a pedir la cuenta y las categorías', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/conciliacion');
      backend.expectOne(`${BASE}/cuentas/5`).flush({}, { status: 500, statusText: 'Error' });
      backend.expectOne((p) => p.url === URL_CONCILIACION).flush([]);
      await estable();

      expect(texto()).toContain('No pudimos cargar la cuenta.');
      // La de categorías quedó cancelada al fallar la cuenta (forkJoin).
      expect(backend.match((p) => p.url === `${BASE}/categorias`)[0].cancelled).toBe(true);
      boton('Reintentar')?.click();
      await estable();
      backend.expectOne(`${BASE}/cuentas/5`).flush(cuenta());
      backend.expectOne((p) => p.url === `${BASE}/categorias`).flush(GRUPOS);
      await estable();
      expect(texto()).toContain('Saldo del extracto');
    });

    it('un error del historial no tapa el asistente y Reintentar lo vuelve a pedir', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/conciliacion');
      backend.expectOne(`${BASE}/cuentas/5`).flush(cuenta());
      backend.expectOne((p) => p.url === `${BASE}/categorias`).flush(GRUPOS);
      backend
        .expectOne((p) => p.url === URL_CONCILIACION)
        .flush({}, { status: 500, statusText: 'E' });
      await estable();

      expect(texto()).toContain('No pudimos cargar el historial.');
      expect(entradaSaldo()).not.toBeNull();
      boton('Reintentar')?.click();
      await estable();
      backend.expectOne((p) => p.url === URL_CONCILIACION).flush([]);
      await estable();
      expect(texto()).toContain('Aún no has conciliado esta cuenta');
    });

    it('una cuenta cerrada muestra el aviso y el historial, sin asistente', async () => {
      await entrar(cuenta({ nombre: 'Vieja', cerrada: true }), [conciliacion()]);
      await esperar();

      expect(texto()).toContain('La cuenta está cerrada; reábrela en Cuentas para conciliarla');
      expect(elemento().querySelectorAll('li.conciliacion')).toHaveLength(1);
      expect(entradaSaldo()).toBeNull();
      expect(boton('Reconciliar')).toBeUndefined();
      expect(peticionesEstado()).toHaveLength(0);
    });
  });

  describe('estado mientras se escribe', () => {
    it('hace una sola consulta 300 ms después del último cambio', async () => {
      await entrar();
      await saldo('1');
      await esperar(100);
      await saldo('15');
      await esperar(100);
      await saldo('150');
      await esperar(ESPERA_ESTADO_MS - 50);
      expect(peticionesEstado()).toHaveLength(0);

      await esperar(50);
      const [peticion, ...resto] = peticionesEstado();
      expect(resto).toHaveLength(0);
      expect(peticion.request.params.get('saldoExtracto')).toBe('150000');
      expect(peticion.request.params.get('fecha')).toBe('2026-10-08');
      peticion.flush(estadoCon(peticion, 0));
    });

    it.each([
      ['America/New_York', 23],
      ['Asia/Tokyo', 1],
    ])('en %s la fecha por defecto es la de hoy local (hora %i)', async (zona, hora) => {
      process.env['TZ'] = zona;
      vi.setSystemTime(new Date(2026, 9, 8, hora));
      await entrar();
      await saldo('150');
      await esperar();

      const peticion = backend.expectOne((p) => p.url === `${URL_CONCILIACION}/estado`);
      expect(peticion.request.params.get('fecha')).toBe('2026-10-08');
      peticion.flush(estadoCon(peticion, 0));
    });

    it('sin saldo no consulta nada', async () => {
      await entrar();
      await esperar();

      expect(peticionesEstado()).toHaveLength(0);
      expect(reconciliarBoton().disabled).toBe(true);
    });

    it('con una fecha futura avisa, no consulta y no se puede reconciliar', async () => {
      await entrar();
      await fecha('10/9/2026');
      await saldo('150');
      await esperar();

      expect(texto()).toContain('La fecha no puede ser futura');
      expect(peticionesEstado()).toHaveLength(0);
      expect(reconciliarBoton().disabled).toBe(true);
    });

    it('usa la fecha elegida como yyyy-MM-dd', async () => {
      await entrar();
      await fecha('9/30/2026');
      await saldo('150');
      await esperar();

      const peticion = backend.expectOne((p) => p.url === `${URL_CONCILIACION}/estado`);
      expect(peticion.request.params.get('fecha')).toBe('2026-09-30');
      peticion.flush(estadoCon(peticion, 0));
    });

    it('ignora la respuesta de una consulta anterior', async () => {
      await entrar();
      await saldo('100');
      await esperar();
      const [vieja] = peticionesEstado();
      await saldo('150');
      await esperar();
      const [nueva] = peticionesEstado();

      expect(vieja.cancelled).toBe(true);
      nueva.flush(estadoCon(nueva, 30000));
      await estable();
      expect(texto()).toContain('A lo conciliado le faltan $30.00');
    });

    it('mientras consulta muestra un indicador', async () => {
      await entrar();
      await saldo('150');

      expect(elemento().querySelector('[aria-label="Calculando la diferencia"]')).not.toBeNull();
      await responderEstado(0);
      expect(elemento().querySelector('[aria-label="Calculando la diferencia"]')).toBeNull();
    });

    it('si la consulta falla muestra el mensaje y Reintentar vuelve a consultar', async () => {
      await entrar();
      await saldo('150');
      await esperar();
      backend
        .expectOne((p) => p.url === `${URL_CONCILIACION}/estado`)
        .flush({}, { status: 500, statusText: 'Error' });
      await estable();

      expect(texto()).toContain('No pudimos calcular la diferencia');
      boton('Reintentar')?.click();
      await estable();
      await responderEstado(0);
      expect(texto()).toContain('Cuadra con el extracto');
    });

    it.each([
      [0, 'Cuadra con el extracto'],
      [30000, 'A lo conciliado le faltan $30.00'],
      [-5000, 'Lo conciliado supera al extracto en $5.00'],
    ])('con diferencia %i dice "%s"', async (diferencia, esperado) => {
      await conDiferencia(diferencia);

      expect(normalizar(elemento().querySelector('.diferencia')?.textContent)).toBe(esperado);
    });

    it('muestra el saldo conciliado al corte', async () => {
      await entrar();
      await fecha('9/30/2026');
      await saldo('150');
      await responderEstado(30000);

      expect(texto()).toContain('Saldo conciliado al September 30, 2026: $120.00');
    });
  });

  describe('no conciliadas hasta la fecha', () => {
    it('cuenta las de fecha hasta la del extracto y enlaza a Transacciones filtrada', async () => {
      await entrar();
      await fecha('9/30/2026');
      await saldo('150');
      await responderEstado(0, {
        totalNoConciliadas: 3,
        noConciliadas: [
          pendiente(3, '2026-10-02'),
          pendiente(2, '2026-09-30'),
          pendiente(1, '2026-09-12'),
        ],
      });

      expect(texto()).toContain(
        'Hay 2 transacciones no conciliadas hasta esa fecha que no se reconciliarán.',
      );
      const enlace = elemento().querySelector('a.revisar') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe(
        '/presupuestos/3/transacciones?cuentaId=5&estado=NO_CONCILIADA&hasta=2026-09-30',
      );
    });

    it('con la lista recortada dice "al menos"', async () => {
      await entrar();
      await saldo('150');
      await responderEstado(0, {
        totalNoConciliadas: 130,
        noConciliadas: Array.from({ length: 100 }, (_, i) => pendiente(i + 1, '2026-09-01')),
      });

      expect(texto()).toContain('Hay al menos 100 transacciones no conciliadas');
    });

    it('sin pendientes hasta la fecha no muestra el enlace', async () => {
      await entrar();
      await saldo('150');
      await responderEstado(0, {
        totalNoConciliadas: 1,
        noConciliadas: [pendiente(1, '2026-10-08')],
      });

      expect(elemento().querySelector('a.revisar')).not.toBeNull();
      await saldo('151');
      await responderEstado(0, { totalNoConciliadas: 0, noConciliadas: [] });
      expect(elemento().querySelector('a.revisar')).toBeNull();
    });
  });

  describe('ajuste y su categoría', () => {
    it('con diferencia y sin ajuste no se puede reconciliar y lo explica', async () => {
      await conDiferencia(-5000);

      expect(texto()).toContain('Crear transacción de ajuste por -$5.00 (salida)');
      expect(texto()).toContain(MENSAJE_SIN_AJUSTE);
      expect(reconciliarBoton().disabled).toBe(true);
    });

    it('negativa del presupuesto: categoría obligatoria, sin ocultas ni de pago', async () => {
      await conDiferencia(-5000);
      await marcarAjuste();

      expect(texto()).toContain('El ajuste sale del dinero de una categoría');
      expect(reconciliarBoton().disabled).toBe(true);
      const select = await categoria();
      await select.open();
      const grupos = await select.getOptionGroups();
      expect(await Promise.all(grupos.map((g) => g.getLabelText()))).toEqual(['Gastos']);
      expect(await Promise.all((await select.getOptions()).map((o) => o.getText()))).toEqual([
        'Comisiones',
        'Comida',
      ]);
      await select.clickOptions({ text: 'Comisiones' });
      await estable();

      expect(reconciliarBoton().disabled).toBe(false);
      const peticion = await reconciliar();
      expect(peticion.request.body).toEqual({
        saldoExtracto: 150000,
        fecha: '2026-10-08',
        crearAjuste: true,
        categoriaId: 7,
      });
      peticion.flush(conciliacion({ ajuste: -5000 }));
      await estable();
      await responderEstado(0);
      backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET').flush([]);
    });

    it('positiva en una cuenta corriente: categoría opcional, se envía null', async () => {
      await conDiferencia(2000);
      await marcarAjuste();

      expect(texto()).toContain('Sin categoría cuenta como ingreso');
      expect(reconciliarBoton().disabled).toBe(false);
      const peticion = await reconciliar();
      expect(peticion.request.body).toEqual({
        saldoExtracto: 150000,
        fecha: '2026-10-08',
        crearAjuste: true,
        categoriaId: null,
      });
      peticion.flush(conciliacion({ ajuste: 2000 }));
      await estable();
      await responderEstado(0);
      backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET').flush([]);
    });

    it('positiva en una tarjeta del presupuesto: categoría obligatoria', async () => {
      await conDiferencia(2000, cuenta({ nombre: 'Visa', tipo: 'TARJETA_CREDITO' }));
      await marcarAjuste();

      expect(texto()).toContain('El ajuste sale del dinero de una categoría');
      expect(reconciliarBoton().disabled).toBe(true);
    });

    it('fuera del presupuesto: sin campo de categoría y sin categoriaId', async () => {
      await conDiferencia(
        -5000,
        cuenta({ nombre: 'Inversiones', tipo: 'INVERSION', enPresupuesto: false }),
      );
      await marcarAjuste();

      expect(campoCategoria()).toBeNull();
      const peticion = await reconciliar();
      expect(peticion.request.body).toEqual({
        saldoExtracto: 150000,
        fecha: '2026-10-08',
        crearAjuste: true,
      });
      peticion.flush(conciliacion({ ajuste: -5000 }));
      await estable();
      await responderEstado(0);
      backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET').flush([]);
    });

    it('si la diferencia pasa a 0 se quitan el ajuste y la categoría', async () => {
      await conDiferencia(-5000);
      await marcarAjuste();
      await (await categoria()).clickOptions({ text: 'Comisiones' });
      await estable();

      await saldo('145');
      await responderEstado(0);
      expect(campoCategoria()).toBeNull();
      expect(casilla()).toBeNull();

      await saldo('150');
      await responderEstado(-5000);
      expect(casilla()?.checked).toBe(false);
      await marcarAjuste();
      expect(await (await categoria()).getValueText()).toBe('');
      expect(reconciliarBoton().disabled).toBe(true);
    });
  });

  describe('confirmar y reconciliar', () => {
    it('sin diferencia confirma, envía el cuerpo y muestra el resultado', async () => {
      await entrar();
      await fecha('9/30/2026');
      await saldo('150');
      await responderEstado(0);

      const peticion = await reconciliar();
      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoConfirmarConciliacionComponent,
        expect.objectContaining({
          data: {
            cuenta: 'Banco',
            fecha: '2026-09-30',
            saldoExtracto: 150000,
            ajuste: null,
            moneda: 'USD',
          },
        }),
      );
      expect(peticion.request.body).toEqual({
        saldoExtracto: 150000,
        fecha: '2026-09-30',
        crearAjuste: false,
      });
      expect(reconciliarBoton().disabled).toBe(true);
      peticion.flush(conciliacion({ cantidadReconciliadas: 4 }));
      await estable();

      expect(texto()).toContain('Se reconciliaron 4 transacciones');
      expect(texto()).not.toContain('Se creó un ajuste');
      // Vuelve a pedir el estado y el historial.
      backend
        .expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET')
        .flush([conciliacion()]);
      await responderEstado(0);
      expect(elemento().querySelectorAll('li.conciliacion')).toHaveLength(1);
    });

    it('con ajuste, el resumen lo incluye y el resultado lo muestra', async () => {
      await conDiferencia(2000);
      await marcarAjuste();

      const peticion = await reconciliar();
      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoConfirmarConciliacionComponent,
        expect.objectContaining({ data: expect.objectContaining({ ajuste: 2000 }) }),
      );
      peticion.flush(conciliacion({ ajuste: 2000, cantidadReconciliadas: 5 }));
      await estable();

      expect(texto()).toContain('Se reconciliaron 5 transacciones');
      expect(texto()).toContain('Se creó un ajuste de $2.00');
      backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET').flush([]);
      await responderEstado(0);
    });

    it('muestra el resultado real aunque difiera del previsto', async () => {
      await conDiferencia(-5000, cuenta({ enPresupuesto: false }));
      await marcarAjuste();

      const peticion = await reconciliar();
      peticion.flush(conciliacion({ ajuste: 0, cantidadReconciliadas: 3 }));
      await estable();

      expect(texto()).toContain('Se reconciliaron 3 transacciones');
      expect(texto()).not.toContain('Se creó un ajuste');
      backend.expectOne((p) => p.url === URL_CONCILIACION && p.method === 'GET').flush([]);
      await responderEstado(0);
    });

    it('cancelar la confirmación no envía nada', async () => {
      confirmar = false;
      await conDiferencia(0);

      reconciliarBoton().click();
      await estable();

      expect(abrirDialogo).toHaveBeenCalled();
      backend.expectNone((p) => p.method === 'POST');
      expect(reconciliarBoton().disabled).toBe(false);
    });
  });

  describe('errores al reconciliar', () => {
    async function fallar(status: number, cuerpo: object): Promise<void> {
      await conDiferencia(0);
      const peticion = await reconciliar();
      peticion.flush(cuerpo, { status, statusText: 'Error' });
      await estable();
    }

    it('400 sin errores pide revisar el saldo y la fecha', async () => {
      await fallar(400, { codigo: 'DATOS_INVALIDOS' });

      expect(texto()).toContain(MENSAJE_DATOS_EXTRACTO);
      expect(reconciliarBoton().disabled).toBe(false);
    });

    it('400 con errores los muestra en sus campos', async () => {
      await fallar(400, { codigo: 'DATOS_INVALIDOS', errores: { fecha: 'Fecha rechazada' } });

      expect(texto()).toContain('Fecha rechazada');
      expect(reconciliarBoton().disabled).toBe(true);
    });

    it('422 muestra el mensaje y conserva el saldo y la fecha', async () => {
      await fallar(422, { codigo: 'REGLA_NEGOCIO_VIOLADA' });

      expect(texto()).toContain(MENSAJE_REGLA_CONCILIACION);
      expect(entradaSaldo()?.value).toBe('150');
      expect(entradaFecha()?.value).toBe('10/08/2026');
    });

    it('404 avisa y vuelve a pedir cuenta, categorías, estado e historial', async () => {
      await fallar(404, { codigo: 'RECURSO_NO_ENCONTRADO' });

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_REFERENCIA_INEXISTENTE, 'Cerrar', {
        duration: 6000,
      });
      responderCarga();
      await estable();
      await responderEstado(0);
      expect(texto()).toContain('Cuadra con el extracto');
    });

    it('otro error muestra el aviso genérico', async () => {
      await fallar(500, {});

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
    });
  });
});
