import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoConfirmacionComponent } from '../components/dialogo-confirmacion.component';
import { DialogoMoverCuentaComponent } from '../components/dialogo-mover-cuenta.component';
import { DialogoTransaccionComponent } from '../components/dialogo-transaccion.component';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { filtrosVacios } from '../services/filtros-url';
import {
  MENSAJE_ACCION_NO_PERMITIDA,
  MENSAJE_LOTE_SIN_APLICABLES,
  TransaccionesPage,
} from './transacciones.page';

const BASE = '/api/v1/presupuestos/3';
const URL_TX = `${BASE}/transacciones`;

function transaccion(id: number, cambios: Partial<TransaccionResponse> = {}): TransaccionResponse {
  return {
    id,
    cuentaId: 5,
    fecha: '2026-10-06',
    monto: -25000,
    categoriaId: 7,
    beneficiario: `Beneficiario ${id}`,
    memo: null,
    estado: 'NO_CONCILIADA',
    aprobada: true,
    subtransacciones: [],
    transaccionParId: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}

function pagina(contenido: TransaccionResponse[], extra: object = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: 1,
    ...extra,
  };
}

const SALDOS = [
  { cuentaId: 5, saldo: 150000, saldoConciliado: 100000 },
  { cuentaId: 6, saldo: -20000, saldoConciliado: -20000 },
];

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

describe('TransaccionesPage', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let accionAviso: Subject<void>;
  let resultadosDialogo: Map<unknown, unknown>;
  let abrirDialogo: ReturnType<typeof vi.spyOn>;

  const elemento = () => harness.routeNativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);
  const url = () => TestBed.inject(Router).url;
  const componente = (selector: string) => harness.fixture.debugElement.query(By.css(selector));

  async function estable(): Promise<void> {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
  }

  function peticionLista(): TestRequest {
    return backend.expectOne((p) => p.url === URL_TX);
  }

  /** Responde las listas de cuentas y categorías (solo la primera vez). */
  function responderListas(): void {
    backend
      .expectOne((p) => p.url === `${BASE}/cuentas`)
      .flush([
        { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false },
        { id: 6, nombre: 'Efectivo', enPresupuesto: true, cerrada: false },
      ]);
    backend
      .expectOne((p) => p.url === `${BASE}/categorias`)
      .flush([
        {
          id: 1,
          nombre: 'Necesidades',
          oculto: false,
          categorias: [{ id: 7, nombre: 'Comida', oculta: false }],
        },
      ]);
  }

  async function responder(
    contenido: TransaccionResponse[] = [transaccion(1), transaccion(2, { monto: 100000 })],
    extra: object = {},
  ): Promise<void> {
    peticionLista().flush(pagina(contenido, extra));
    backend.expectOne(`${URL_TX}/saldos`).flush(SALDOS);
    await estable();
  }

  async function entrar(query = '', contenido?: TransaccionResponse[]): Promise<void> {
    await harness.navigateByUrl(`/presupuestos/3/transacciones${query}`);
    responderListas();
    await responder(contenido);
  }

  function accion(tipo: string, t: TransaccionResponse): void {
    componente('app-tabla-transacciones').triggerEventHandler('accion', { tipo, transaccion: t });
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'presupuestos/:presupuestoId/transacciones', component: TransaccionesPage },
        ]),
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
    resultadosDialogo = new Map();
    abrirDialogo = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockImplementation(
        (componenteDialogo: unknown) =>
          ({ afterClosed: () => of(resultadosDialogo.get(componenteDialogo)) }) as MatDialogRef<
            unknown,
            unknown
          >,
      );
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('lista, filtros y URL', () => {
    it('lee los filtros de la URL y los envía con sus nombres de la API', async () => {
      await harness.navigateByUrl(
        '/presupuestos/3/transacciones?cuentaId=5&estado=CONCILIADA&sinAprobar=1&q=luz&pagina=1&tamano=50',
      );
      responderListas();
      const peticion = peticionLista();
      const p = peticion.request.params;

      expect(p.get('cuentaId')).toBe('5');
      expect(p.get('estado')).toBe('CONCILIADA');
      expect(p.get('soloSinAprobar')).toBe('true');
      expect(p.get('q')).toBe('luz');
      expect(p.get('page')).toBe('1');
      expect(p.get('size')).toBe('50');
      peticion.flush(pagina([]));
      backend.expectOne(`${URL_TX}/saldos`).flush(SALDOS);
      await estable();
    });

    it('cambiar un filtro lo guarda en la URL y vuelve a la página 0', async () => {
      await entrar('?pagina=2');

      componente('app-filtros-transacciones').triggerEventHandler('cambiar', {
        ...filtrosVacios(),
        categoriaId: 7,
        desde: '2026-10-01',
        pagina: 2,
      });
      await estable();

      expect(url()).toBe('/presupuestos/3/transacciones?categoriaId=7&desde=2026-10-01');
      const peticion = peticionLista();
      expect(peticion.request.params.get('page')).toBe('0');
      expect(peticion.request.params.get('desde')).toBe('2026-10-01');
      peticion.flush(pagina([]));
      await estable();
    });

    it('el paginador pide la página del servidor y la guarda en la URL', async () => {
      await entrar('', [transaccion(1)]);

      componente('mat-paginator').triggerEventHandler('page', {
        pageIndex: 1,
        pageSize: 20,
        previousPageIndex: 0,
        length: 45,
      });
      await estable();

      expect(url()).toBe('/presupuestos/3/transacciones?pagina=1');
      const peticion = peticionLista();
      expect(peticion.request.params.get('page')).toBe('1');
      peticion.flush(pagina([], { pagina: 1, totalElementos: 45 }));
      await estable();
    });

    it('ignora la respuesta atrasada de una petición vieja', async () => {
      await harness.navigateByUrl('/presupuestos/3/transacciones');
      responderListas();
      const vieja = peticionLista();
      backend.expectOne(`${URL_TX}/saldos`).flush(SALDOS);

      await harness.navigateByUrl('/presupuestos/3/transacciones?q=nuevo');
      expect(vieja.cancelled).toBe(true);
      peticionLista().flush(pagina([transaccion(9, { beneficiario: 'Nuevo' })]));
      await estable();

      expect(texto()).toContain('Nuevo');
    });

    it('muestra Salida y Entrada en sus columnas', async () => {
      await entrar();

      const filas = elemento().querySelectorAll('tr.fila');
      expect(normalizar(filas[0].querySelector('td.salida')?.textContent)).toContain('$25.00');
      expect(normalizar(filas[1].querySelector('td.entrada')?.textContent)).toContain('$100.00');
    });
  });

  describe('saldos', () => {
    it('sin filtro de cuenta muestra la suma de todas', async () => {
      await entrar();

      expect(texto()).toContain('Saldo de todas las cuentas: $130.00');
      expect(texto()).toContain('Conciliado: $80.00');
    });

    it('con filtro de cuenta muestra el saldo de esa cuenta', async () => {
      await entrar('?cuentaId=5');

      expect(texto()).toContain('Saldo de Banco: $150.00');
      expect(texto()).toContain('Conciliado: $100.00');
    });
  });

  describe('estados', () => {
    it('mientras carga muestra el spinner', async () => {
      await harness.navigateByUrl('/presupuestos/3/transacciones');

      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      responderListas();
      await responder();
    });

    it('sin transacciones ni filtros: "Aún no hay transacciones"', async () => {
      await entrar('', []);

      expect(texto()).toContain('Aún no hay transacciones');
    });

    it('con filtros y sin resultados ofrece limpiarlos', async () => {
      await entrar('?estado=RECONCILIADA', []);

      expect(texto()).toContain('Ningún resultado con estos filtros');
      Array.from(elemento().querySelectorAll('button'))
        .find((b) => b.textContent?.trim() === 'Limpiar filtros')
        ?.click();
      await estable();

      expect(url()).toBe('/presupuestos/3/transacciones');
      peticionLista().flush(pagina([]));
      await estable();
    });

    it('un error muestra el aviso con Reintentar', async () => {
      await harness.navigateByUrl('/presupuestos/3/transacciones');
      responderListas();
      peticionLista().flush({}, { status: 500, statusText: 'Error' });
      backend.expectOne(`${URL_TX}/saldos`).flush(SALDOS);
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar las transacciones.');

      accionAviso.next();
      await estable();
      await responder();
      expect(elemento().querySelectorAll('tr.fila')).toHaveLength(2);
    });
  });

  describe('crear y acciones de fila', () => {
    beforeEach(() => entrar());

    it('Agregar transacción abre el diálogo y, al guardar, recarga la página y los saldos', async () => {
      resultadosDialogo.set(DialogoTransaccionComponent, { tipo: 'guardada' });

      Array.from(elemento().querySelectorAll('button'))
        .find((b) => b.textContent?.includes('Agregar transacción'))
        ?.click();
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoTransaccionComponent,
        expect.objectContaining({ data: expect.objectContaining({ transaccion: null }) }),
      );
      await responder();
    });

    it('Editar abre el diálogo con la transacción', async () => {
      accion('editar', transaccion(1));
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoTransaccionComponent,
        expect.objectContaining({ data: expect.objectContaining({ transaccion: transaccion(1) }) }),
      );
    });

    it('Aprobar, cambiar el estado y duplicar llaman a su endpoint y recargan', async () => {
      accion('aprobar', transaccion(1));
      backend.expectOne(`${URL_TX}/1/aprobar`).flush(transaccion(1));
      await estable();
      await responder();

      accion('estado', transaccion(1));
      const estado = backend.expectOne(`${URL_TX}/1/estado`);
      expect(estado.request.body).toEqual({ estado: 'CONCILIADA' });
      estado.flush(transaccion(1));
      await estable();
      await responder();

      accion('duplicar', transaccion(1));
      backend.expectOne(`${URL_TX}/1/duplicar`).flush(transaccion(3));
      await estable();
      expect(abrirAviso).toHaveBeenCalledWith('Transacción duplicada', 'Cerrar', {
        duration: 4000,
      });
      await responder();
    });

    it('Mover abre su diálogo y recarga si se movió', async () => {
      resultadosDialogo.set(DialogoMoverCuentaComponent, transaccion(1, { cuentaId: 6 }));

      accion('mover', transaccion(1));
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoMoverCuentaComponent,
        expect.objectContaining({ data: expect.objectContaining({ transaccion: transaccion(1) }) }),
      );
      await responder();
    });

    it('Borrar pide confirmación y borra solo si se confirma', async () => {
      resultadosDialogo.set(DialogoConfirmacionComponent, false);
      accion('borrar', transaccion(1));
      await estable();
      backend.expectNone(`${URL_TX}/1`);

      resultadosDialogo.set(DialogoConfirmacionComponent, true);
      accion('borrar', transaccion(1));
      const peticion = backend.expectOne(`${URL_TX}/1`);
      expect(peticion.request.method).toBe('DELETE');
      peticion.flush(null);
      await estable();
      await responder();
    });

    it('un 422 en una acción avisa con su motivo y recarga', async () => {
      accion('aprobar', transaccion(1));
      backend
        .expectOne(`${URL_TX}/1/aprobar`)
        .flush({ codigo: 'REGLA_NEGOCIO_VIOLADA' }, { status: 422, statusText: 'Unprocessable' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ACCION_NO_PERMITIDA, 'Cerrar', {
        duration: 6000,
      });
      await responder();
    });
  });

  describe('lote', () => {
    const simple = transaccion(1);
    const pata = transaccion(2, { transaccionParId: 20, aprobada: false });
    const reconciliada = transaccion(3, { estado: 'RECONCILIADA' });

    beforeEach(() => entrar('', [simple, pata, reconciliada]));

    function seleccionar(...ids: number[]): Promise<void> {
      for (const id of ids) {
        componente('app-tabla-transacciones').triggerEventHandler('alternarSeleccion', id);
      }
      return estable();
    }

    it('al seleccionar aparece la barra con la cantidad, y Aprobar manda todos los ids', async () => {
      await seleccionar(1, 2, 3);
      expect(texto()).toContain('3 seleccionadas');

      componente('app-barra-lote').triggerEventHandler('aprobar');
      const peticion = backend.expectOne(`${URL_TX}/lote`);
      expect(peticion.request.body).toEqual({ ids: [1, 2, 3], operacion: 'APROBAR' });
      peticion.flush({ afectadas: 3 });
      await estable();

      expect(elemento().querySelector('app-barra-lote')).toBeNull();
      await responder();
    });

    it('Categorizar excluye la pata y la reconciliada, avisa y confirma', async () => {
      resultadosDialogo.set(DialogoConfirmacionComponent, true);
      await seleccionar(1, 2, 3);

      componente('app-barra-lote').triggerEventHandler('categorizar', 7);
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoConfirmacionComponent,
        expect.objectContaining({
          data: expect.objectContaining({ mensaje: expect.stringContaining('Se omitirán 2') }),
        }),
      );
      const peticion = backend.expectOne(`${URL_TX}/lote`);
      expect(peticion.request.body).toEqual({ ids: [1], operacion: 'CATEGORIZAR', categoriaId: 7 });
      peticion.flush({ afectadas: 1 });
      await estable();
      await responder();
    });

    it('Borrar sin filas aplicables avisa sin llamar al backend', async () => {
      await seleccionar(2, 3);

      componente('app-barra-lote').triggerEventHandler('borrar');
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_LOTE_SIN_APLICABLES, 'Cerrar', {
        duration: 6000,
      });
      backend.expectNone(`${URL_TX}/lote`);
    });

    it('Borrar siempre confirma y no envía si se cancela', async () => {
      resultadosDialogo.set(DialogoConfirmacionComponent, false);
      await seleccionar(1);

      componente('app-barra-lote').triggerEventHandler('borrar');
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(DialogoConfirmacionComponent, expect.anything());
      backend.expectNone(`${URL_TX}/lote`);
    });

    it('seleccionar la página marca todas y cambiar de página limpia la selección', async () => {
      componente('app-tabla-transacciones').triggerEventHandler('seleccionarPagina', true);
      await estable();
      expect(texto()).toContain('3 seleccionadas');

      componente('mat-paginator').triggerEventHandler('page', {
        pageIndex: 1,
        pageSize: 20,
        previousPageIndex: 0,
        length: 60,
      });
      await estable();

      expect(elemento().querySelector('app-barra-lote')).toBeNull();
      peticionLista().flush(pagina([], { pagina: 1 }));
      await estable();
    });

    it('un error del lote avisa y recarga', async () => {
      await seleccionar(1);
      componente('app-barra-lote').triggerEventHandler('aprobar');
      backend.expectOne(`${URL_TX}/lote`).flush({}, { status: 500, statusText: 'Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
      await responder();
    });
  });
});
