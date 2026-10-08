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
import { MatMenuHarness } from '@angular/material/menu/testing';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoAutoAsignarComponent } from '../components/dialogo-auto-asignar.component';
import { DialogoMetaComponent } from '../components/dialogo-meta.component';
import { DialogoMoverDineroComponent } from '../components/dialogo-mover-dinero.component';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { MesPresupuestoResponse } from '../models/mes-presupuesto-response.model';
import { MetaMesResponse, MetasMesResponse } from '../models/metas-mes-response.model';
import {
  MENSAJE_META_QUITADA,
  PresupuestoMensualPage,
  mensajeAutoAsignado,
} from './presupuesto-mensual.page';

const BASE = '/api/v1/presupuestos/3/meses';

function categoria(
  categoriaId: number,
  nombre: string,
  asignado: number,
  actividad: number,
  disponible: number,
  extra: Partial<CategoriaMesResponse> = {},
): CategoriaMesResponse {
  return {
    categoriaId,
    nombre,
    oculta: false,
    asignado,
    actividad,
    disponible,
    sobregastada: disponible < 0,
    esPagoTarjeta: false,
    cuentaId: null,
    ...extra,
  };
}

function mesDePrueba(mes = '2026-10', cambios: Partial<MesPresupuestoResponse> = {}) {
  const respuesta: MesPresupuestoResponse = {
    mes,
    listoParaAsignar: 500000,
    totalAsignado: 150000,
    totalActividad: -120000,
    totalDisponible: 30000,
    grupos: [
      {
        id: 1,
        nombre: 'Facturas',
        orden: 0,
        oculto: false,
        categorias: [
          categoria(7, 'Luz', 100000, -40000, 60000),
          categoria(8, 'Agua', 50000, -80000, -30000),
        ],
      },
      {
        id: 2,
        nombre: 'Deseos',
        orden: 1,
        oculto: false,
        categorias: [categoria(9, 'Ocio', 0, 0, 0)],
      },
    ],
    ...cambios,
  };
  return respuesta;
}

function metaDePrueba(cambios: Partial<MetaMesResponse> = {}): MetaMesResponse {
  return {
    categoriaId: 7,
    nombre: 'Luz',
    tipo: 'MONTO_MENSUAL',
    monto: 100000,
    necesidad: 100000,
    asignado: 60000,
    disponible: 60000,
    faltante: 40000,
    estado: 'FALTA',
    ...cambios,
  };
}

function metasDePrueba(mes = '2026-10', metas: MetaMesResponse[] = []): MetasMesResponse {
  return { mes, totalFaltante: metas.reduce((suma, m) => suma + m.faltante, 0), metas };
}

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

@Component({ template: '<p>Categorías</p>' })
class CategoriasDePrueba {}

describe('PresupuestoMensualPage', () => {
  let harness: RouterTestingHarness;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let accionAviso: Subject<void>;

  const elemento = () => harness.routeNativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);
  const url = () => TestBed.inject(Router).url;
  const celda = (nombre: string) =>
    elemento().querySelector(
      `button[aria-label="Editar el asignado de ${nombre}"]`,
    ) as HTMLButtonElement | null;
  const totalDelMes = (clase: string) =>
    normalizar(elemento().querySelector(`.fila.total .${clase}`)?.textContent);

  async function estable(): Promise<void> {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
  }

  function peticionMes(mes = '2026-10', ocultas = false): TestRequest {
    return backend.expectOne(
      (p) => p.url === `${BASE}/${mes}` && p.params.get('incluirOcultas') === String(ocultas),
    );
  }

  function peticionMetas(mes = '2026-10', ocultas = false): TestRequest {
    return backend.expectOne(
      (p) => p.url === `${BASE}/${mes}/metas` && p.params.get('incluirOcultas') === String(ocultas),
    );
  }

  /** Responde el mes y sus metas (se piden juntos); por defecto, sin metas. */
  async function responder(
    mes = '2026-10',
    respuesta = mesDePrueba(mes),
    opciones: { ocultas?: boolean; metas?: MetasMesResponse } = {},
  ): Promise<void> {
    const ocultas = opciones.ocultas ?? false;
    peticionMes(mes, ocultas).flush(respuesta);
    peticionMetas(mes, ocultas).flush(opciones.metas ?? metasDePrueba(mes));
    await estable();
  }

  async function entrar(
    mes = '2026-10',
    respuesta = mesDePrueba(mes),
    metas = metasDePrueba(mes),
  ): Promise<void> {
    await harness.navigateByUrl(`/presupuestos/3/presupuesto/${mes}`);
    await responder(mes, respuesta, { metas });
  }

  async function editar(nombre: string, valor: string, tecla = 'Enter'): Promise<void> {
    celda(nombre)?.click();
    await estable();
    const entrada = elemento().querySelector('app-celda-asignado input') as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(
      new KeyboardEvent('keydown', { key: tecla, bubbles: true, cancelable: true }),
    );
    await estable();
  }

  function simularDialogo(resultado: unknown) {
    return vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(resultado),
    } as MatDialogRef<unknown, unknown>);
  }

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 6, 12));
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          {
            path: 'presupuestos/:presupuestoId/presupuesto/:mes',
            component: PresupuestoMensualPage,
          },
          { path: 'presupuestos/:presupuestoId/categorias', component: CategoriasDePrueba },
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
    harness = await RouterTestingHarness.create();
    cargador = TestbedHarnessEnvironment.loader(harness.fixture);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('mes y navegación', () => {
    it.each(['2026-13', '1999-12', 'octubre'])(
      'un mes inválido (%s) lleva al mes actual local',
      async (mes) => {
        await harness.navigateByUrl(`/presupuestos/3/presupuesto/${mes}`);
        await estable();

        expect(url()).toBe('/presupuestos/3/presupuesto/2026-10');
        await responder();
        await estable();
      },
    );

    it('Mes siguiente navega a la URL del mes y lo pide', async () => {
      await entrar('2026-12', mesDePrueba('2026-12'));

      (elemento().querySelector('button[aria-label="Mes siguiente"]') as HTMLButtonElement).click();
      await estable();

      expect(url()).toBe('/presupuestos/3/presupuesto/2027-01');
      await responder('2027-01');
      await estable();
      expect(texto()).toContain('January 2027');
    });

    it('Hoy vuelve al mes actual', async () => {
      await entrar('2025-03', mesDePrueba('2025-03'));

      Array.from(elemento().querySelectorAll('button'))
        .find((b) => b.textContent?.trim() === 'Hoy')
        ?.click();
      await estable();

      expect(url()).toBe('/presupuestos/3/presupuesto/2026-10');
      await responder();
      await estable();
    });

    it('ignora la respuesta atrasada de un mes anterior', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');
      const vieja = peticionMes('2026-10');
      const metasViejas = peticionMetas('2026-10');

      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-11');
      expect(vieja.cancelled).toBe(true);
      expect(metasViejas.cancelled).toBe(true);
      await responder('2026-11', mesDePrueba('2026-11', { listoParaAsignar: 7000 }));
      await estable();

      expect(texto()).toContain('November 2026');
      expect(texto()).toContain('$7.00');
    });
  });

  describe('contenido del mes', () => {
    beforeEach(() => entrar());

    it('muestra el listo para asignar, los grupos, las columnas y el total del mes', () => {
      expect(texto()).toContain('$500.00');
      expect(texto()).toContain('Listo para asignar');
      expect(texto()).toContain('Facturas');
      expect(texto()).toContain('Deseos');
      const columnas = Array.from(elemento().querySelectorAll('.columnas span')).map(
        (c) => c.textContent,
      );
      expect(columnas).toEqual(['Categoría', 'Asignado', 'Actividad', 'Disponible']);
      expect(totalDelMes('total-asignado')).toBe('$150.00');
      expect(totalDelMes('total-actividad')).toBe('-$120.00');
      expect(totalDelMes('total-disponible')).toBe('$30.00');
    });

    it('Mostrar ocultas vuelve a pedir el mes con incluirOcultas=true', async () => {
      await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
      await estable();

      await responder('2026-10', mesDePrueba(), { ocultas: true });
      await estable();
      expect(texto()).toContain('Facturas');
    });
  });

  describe('estados', () => {
    it('mientras carga muestra el spinner', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');

      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      await responder();
      await estable();
      expect(elemento().querySelector('mat-progress-spinner')).toBeNull();
    });

    it('un error muestra el aviso con Reintentar, que vuelve a pedir el mes', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');
      const metas = peticionMetas();
      peticionMes().flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      // Sin el mes, sus metas ya no se esperan.
      expect(metas.cancelled).toBe(true);
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar el presupuesto del mes.');

      accionAviso.next();
      await estable();
      await responder();
      await estable();
      expect(texto()).toContain('Facturas');
    });

    it('un mes sin categorías muestra el estado vacío con enlace a Categorías', async () => {
      await entrar('2026-10', mesDePrueba('2026-10', { grupos: [] }));

      expect(texto()).toContain('Aún no tienes categorías');
      const enlace = elemento().querySelector('a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/categorias');
    });
  });

  describe('editar el asignado', () => {
    beforeEach(() => entrar());

    it('muestra el valor de inmediato, envía el PUT y aplica la respuesta', async () => {
      await editar('Luz', '150.5');

      // Optimista: asignado, disponible (+50,5) y listo para asignar (-50,5) antes de responder.
      expect(normalizar(celda('Luz')?.textContent)).toBe('$150.50');
      expect(totalDelMes('total-asignado')).toBe('$200.50');
      expect(texto()).toContain('$449.50');

      const peticion = backend.expectOne(`${BASE}/2026-10/categorias/7`);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({ asignado: 150500 });
      peticion.flush({
        categoria: categoria(7, 'Luz', 150500, -40000, 110500),
        listoParaAsignar: 449000,
      });
      await estable();

      expect(texto()).toContain('$449.00');
      expect(totalDelMes('total-disponible')).toBe('$80.50');
      expect(document.activeElement).toBe(celda('Luz'));
    });

    it('un 500 revierte el valor y avisa', async () => {
      await editar('Luz', '200');
      backend
        .expectOne(`${BASE}/2026-10/categorias/7`)
        .flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(normalizar(celda('Luz')?.textContent)).toBe('$100.00');
      expect(texto()).toContain('$500.00');
      expect(totalDelMes('total-asignado')).toBe('$150.00');
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
    });

    it('un 400 con errores.asignado revierte y muestra el mensaje en la celda', async () => {
      await editar('Luz', '200');
      backend
        .expectOne(`${BASE}/2026-10/categorias/7`)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { asignado: 'Valor fuera de rango' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await estable();

      expect(normalizar(celda('Luz')?.textContent)).toBe('$100.00');
      expect(texto()).toContain('Valor fuera de rango');
      expect(abrirAviso).not.toHaveBeenCalled();
    });

    it('un 409 avisa y recarga el mes', async () => {
      await editar('Luz', '200');
      backend
        .expectOne(`${BASE}/2026-10/categorias/7`)
        .flush({ codigo: 'CONFLICTO' }, { status: 409, statusText: 'Conflict' });
      await estable();

      expect(abrirAviso).toHaveBeenCalled();
      await responder();
      await estable();
    });

    it('un segundo guardado de la misma celda en curso se ignora', async () => {
      const grupo = harness.fixture.debugElement.query(By.css('app-grupo-mes'));
      grupo.triggerEventHandler('asignar', { categoriaId: 7, asignado: 1000 });
      grupo.triggerEventHandler('asignar', { categoriaId: 7, asignado: 2000 });
      await estable();

      const peticion = backend.expectOne(`${BASE}/2026-10/categorias/7`);
      expect(peticion.request.body).toEqual({ asignado: 1000 });
      peticion.flush({ categoria: categoria(7, 'Luz', 1000, -40000, -39000), listoParaAsignar: 1 });
      await estable();
    });

    it('un texto inválido no llama al backend', async () => {
      await editar('Luz', 'abc');

      backend.expectNone(`${BASE}/2026-10/categorias/7`);
      expect(texto()).toContain('Monto inválido');
    });
  });

  describe('mover dinero', () => {
    beforeEach(() => entrar());

    it('Mover dinero... abre el diálogo con el origen y muestra el mes devuelto', async () => {
      const devuelto = mesDePrueba('2026-10', { listoParaAsignar: 123000 });
      const abrir = simularDialogo({ tipo: 'movido', mes: devuelto });

      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: '[aria-label="Acciones de Luz"]' }),
      );
      await menu.open();
      await menu.clickItem({ text: /Mover dinero/ });
      await estable();

      expect(abrir).toHaveBeenCalledWith(
        DialogoMoverDineroComponent,
        expect.objectContaining({ data: expect.objectContaining({ origenId: 7 }) }),
      );
      expect(texto()).toContain('$123.00');
    });

    it('Cubrir sobregasto propone el destino y el monto del sobregasto', async () => {
      const abrir = simularDialogo(undefined);

      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: '[aria-label="Acciones de Agua"]' }),
      );
      await menu.open();
      await menu.clickItem({ text: /Cubrir sobregasto/ });
      await estable();

      expect(abrir).toHaveBeenCalledWith(
        DialogoMoverDineroComponent,
        expect.objectContaining({
          data: expect.objectContaining({ destinoId: 8, monto: 30000 }),
        }),
      );
    });

    it('si el diálogo pide recargar, vuelve a pedir el mes', async () => {
      simularDialogo({ tipo: 'recargar' });

      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: '[aria-label="Acciones de Luz"]' }),
      );
      await menu.open();
      await menu.clickItem({ text: /Mover dinero/ });
      await estable();

      await responder();
      await estable();
    });

    it('sin Mostrar ocultas, quita las ocultas del mes devuelto', async () => {
      const devuelto = mesDePrueba();
      devuelto.grupos.push({
        id: 5,
        nombre: 'Grupo oculto',
        orden: 2,
        oculto: true,
        categorias: [categoria(50, 'Escondida', 0, 0, 0)],
      });
      devuelto.grupos[0].categorias.push(categoria(51, 'Vieja', 0, 0, 0, { oculta: true }));
      simularDialogo({ tipo: 'movido', mes: devuelto });

      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: '[aria-label="Acciones de Luz"]' }),
      );
      await menu.open();
      await menu.clickItem({ text: /Mover dinero/ });
      await estable();

      expect(texto()).not.toContain('Grupo oculto');
      expect(texto()).not.toContain('Vieja');
    });
  });

  describe('metas', () => {
    const fila = (nombre: string) =>
      Array.from(elemento().querySelectorAll('.fila.categoria')).find((f) =>
        f.querySelector('.nombre-categoria')?.textContent?.includes(nombre),
      ) as HTMLElement;
    const indicador = (nombre: string) =>
      normalizar(fila(nombre)?.querySelector('app-indicador-meta')?.textContent) || null;
    const botonPorTexto = (t: string) =>
      Array.from(elemento().querySelectorAll('button')).find((b) => b.textContent?.trim() === t) as
        HTMLButtonElement | undefined;

    async function elegirDelMenu(nombre: string, item: RegExp): Promise<void> {
      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: `[aria-label="Acciones de ${nombre}"]` }),
      );
      await menu.open();
      await menu.clickItem({ text: item });
      await estable();
    }

    const METAS = metasDePrueba('2026-10', [
      metaDePrueba(),
      metaDePrueba({ categoriaId: 9, nombre: 'Ocio', faltante: 10000, asignado: 90000 }),
    ]);

    it('muestra el indicador en las filas con meta y el total faltante', async () => {
      await entrar('2026-10', mesDePrueba(), METAS);

      expect(indicador('Luz')).toContain('Falta $40.00');
      expect(indicador('Luz')).toContain('Meta del mes: $100.00');
      expect(indicador('Ocio')).toContain('Falta $10.00');
      expect(indicador('Agua')).toBeNull();
      expect(texto()).toContain('Falta para tus metas: $50.00');
    });

    it('sin faltante no muestra el total de las metas', async () => {
      await entrar();

      expect(texto()).not.toContain('Falta para tus metas');
    });

    it('en un mes pasado el faltante se ve como Faltaron', async () => {
      await entrar(
        '2026-08',
        mesDePrueba('2026-08'),
        metasDePrueba('2026-08', [metaDePrueba({ asignado: 0, faltante: 100000 })]),
      );

      expect(indicador('Luz')).toContain('Faltaron $100.00');
    });

    it('Mostrar ocultas pide el mes y las metas con incluirOcultas=true', async () => {
      await entrar();
      await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
      await estable();

      await responder('2026-10', mesDePrueba(), {
        ocultas: true,
        metas: metasDePrueba('2026-10', [metaDePrueba()]),
      });
      expect(indicador('Luz')).toContain('Falta $40.00');
    });

    it('ignora las metas atrasadas de un mes anterior', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');
      peticionMes('2026-10').flush(mesDePrueba());
      const metasViejas = peticionMetas('2026-10');

      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-11');
      expect(metasViejas.cancelled).toBe(true);
      await responder('2026-11', mesDePrueba('2026-11'), {
        metas: metasDePrueba('2026-11', [metaDePrueba({ faltante: 7000 })]),
      });

      expect(texto()).toContain('November 2026');
      expect(indicador('Luz')).toContain('Falta $7.00');
    });

    it('si solo fallan las metas, muestra el mes sin indicadores y permite reintentar', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');
      peticionMes().flush(mesDePrueba());
      peticionMetas().flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(texto()).toContain('Luz');
      expect(texto()).toContain('No pudimos cargar las metas');
      expect(elemento().querySelector('app-indicador-meta')).toBeNull();
      expect(totalDelMes('total-asignado')).toBe('$150.00');

      botonPorTexto('Reintentar')?.click();
      await estable();
      await responder('2026-10', mesDePrueba(), { metas: METAS });

      expect(texto()).not.toContain('No pudimos cargar las metas');
      expect(indicador('Luz')).toContain('Falta $40.00');
    });

    describe('diálogo de meta', () => {
      it.each([
        ['Agregar meta', 9, 'Ocio', false],
        ['Editar meta', 7, 'Luz', true],
      ] as const)('%s abre el diálogo con la categoría', async (item, id, nombre, tieneMeta) => {
        await entrar('2026-10', mesDePrueba(), metasDePrueba('2026-10', [metaDePrueba()]));
        const abrir = simularDialogo(undefined);

        await elegirDelMenu(nombre, new RegExp(item));

        expect(abrir).toHaveBeenCalledWith(DialogoMetaComponent, {
          data: { categoriaId: id, nombre, tieneMeta },
          width: '480px',
        });
        // El foco vuelve al botón del menú al cerrar: restoreFocus queda por defecto.
        expect(abrir.mock.calls[0][1]).not.toHaveProperty('restoreFocus');
      });

      it.each([{ tipo: 'guardada' }, { tipo: 'quitada' }, { tipo: 'recargar' }])(
        'con el resultado %o vuelve a pedir el mes y sus metas',
        async (resultado) => {
          await entrar();
          simularDialogo(resultado);

          await elegirDelMenu('Luz', /Agregar meta/);

          await responder();
        },
      );

      it('al cancelar no vuelve a pedir nada', async () => {
        await entrar();
        simularDialogo(undefined);

        await elegirDelMenu('Luz', /Agregar meta/);

        backend.expectNone((p) => p.url.startsWith(`${BASE}/2026-10`));
      });
    });

    describe('posponer y reanudar', () => {
      beforeEach(() =>
        entrar(
          '2026-10',
          mesDePrueba(),
          metasDePrueba('2026-10', [
            metaDePrueba(),
            metaDePrueba({ categoriaId: 9, nombre: 'Ocio', estado: 'POSPUESTA', necesidad: 0 }),
          ]),
        ),
      );

      it('Posponer este mes envía el POST, deshabilita la fila y recarga', async () => {
        await elegirDelMenu('Luz', /Posponer este mes/);
        const peticion = backend.expectOne(`${BASE}/2026-10/metas/7/posponer`);
        expect(peticion.request.method).toBe('POST');

        const menu = await cargador.getHarness(
          MatMenuHarness.with({ selector: '[aria-label="Acciones de Luz"]' }),
        );
        await menu.open();
        const items = await menu.getItems();
        expect(await items[items.length - 1].isDisabled()).toBe(true);
        await menu.close();

        peticion.flush(metaDePrueba({ estado: 'POSPUESTA' }));
        await estable();
        await responder('2026-10', mesDePrueba(), {
          metas: metasDePrueba('2026-10', [
            metaDePrueba({ estado: 'POSPUESTA', necesidad: 0, faltante: 0 }),
          ]),
        });

        expect(indicador('Luz')).toContain('Pospuesta este mes');
        expect(indicador('Luz')).toContain('Meta del mes: $0.00');
      });

      it('Reanudar este mes envía /reanudar y recarga', async () => {
        await elegirDelMenu('Ocio', /Reanudar este mes/);
        backend.expectOne(`${BASE}/2026-10/metas/9/reanudar`).flush(metaDePrueba());
        await estable();

        await responder('2026-10', mesDePrueba(), { metas: METAS });
        expect(indicador('Ocio')).toContain('Falta $10.00');
      });

      it('un 404 avisa que ya no tiene meta y recarga', async () => {
        await elegirDelMenu('Luz', /Posponer este mes/);
        backend
          .expectOne(`${BASE}/2026-10/metas/7/posponer`)
          .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
        await estable();

        expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_META_QUITADA, 'Cerrar', {
          duration: 6000,
        });
        await responder();
      });

      it('otro error avisa de forma genérica', async () => {
        await elegirDelMenu('Luz', /Posponer este mes/);
        backend
          .expectOne(`${BASE}/2026-10/metas/7/posponer`)
          .flush({}, { status: 500, statusText: 'Internal Server Error' });
        await estable();

        expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
          duration: 6000,
        });
        await responder();
      });
    });

    describe('auto-asignar', () => {
      it('está deshabilitado mientras no hay datos del mes', async () => {
        await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');

        expect(botonPorTexto('Auto-asignar')?.disabled).toBe(true);
        await responder();
        expect(botonPorTexto('Auto-asignar')?.disabled).toBe(false);
      });

      it('abre el diálogo con las categorías del mes y si tienen meta', async () => {
        const mes = mesDePrueba();
        mes.grupos[1].categorias.push(
          categoria(20, 'Pago: Visa', 0, 0, 0, { esPagoTarjeta: true, cuentaId: 4 }),
        );
        await entrar('2026-10', mes, metasDePrueba('2026-10', [metaDePrueba()]));
        const abrir = simularDialogo(undefined);

        botonPorTexto('Auto-asignar')?.click();
        await estable();

        expect(abrir).toHaveBeenCalledWith(DialogoAutoAsignarComponent, {
          data: {
            mes: '2026-10',
            categorias: [
              { categoriaId: 7, nombre: 'Luz', esPagoTarjeta: false, tieneMeta: true },
              { categoriaId: 8, nombre: 'Agua', esPagoTarjeta: false, tieneMeta: false },
              { categoriaId: 9, nombre: 'Ocio', esPagoTarjeta: false, tieneMeta: false },
              { categoriaId: 20, nombre: 'Pago: Visa', esPagoTarjeta: true, tieneMeta: false },
            ],
          },
          width: '520px',
        });
        expect(abrir.mock.calls[0][1]).not.toHaveProperty('restoreFocus');
      });

      it('tras aplicar avisa la cantidad real de categorías y recarga', async () => {
        await entrar();
        simularDialogo({ tipo: 'aplicado', cambios: 2 });

        botonPorTexto('Auto-asignar')?.click();
        await estable();

        expect(abrirAviso).toHaveBeenCalledWith('Se actualizaron 2 categorías', 'Cerrar', {
          duration: 6000,
        });
        await responder();
      });

      it('si el diálogo pide recargar, vuelve a pedir el mes sin aviso', async () => {
        await entrar();
        simularDialogo({ tipo: 'recargar' });

        botonPorTexto('Auto-asignar')?.click();
        await estable();

        expect(abrirAviso).not.toHaveBeenCalled();
        await responder();
      });

      it('mensajeAutoAsignado usa el singular con una categoría', () => {
        expect(mensajeAutoAsignado(1)).toBe('Se actualizó 1 categoría');
        expect(mensajeAutoAsignado(0)).toBe('Se actualizaron 0 categorías');
      });
    });
  });
});
