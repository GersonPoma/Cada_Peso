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
import { DialogoMoverDineroComponent } from '../components/dialogo-mover-dinero.component';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { MesPresupuestoResponse } from '../models/mes-presupuesto-response.model';
import { PresupuestoMensualPage } from './presupuesto-mensual.page';

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

  async function entrar(mes = '2026-10', respuesta = mesDePrueba(mes)): Promise<void> {
    await harness.navigateByUrl(`/presupuestos/3/presupuesto/${mes}`);
    peticionMes(mes).flush(respuesta);
    await estable();
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
        peticionMes('2026-10').flush(mesDePrueba());
        await estable();
      },
    );

    it('Mes siguiente navega a la URL del mes y lo pide', async () => {
      await entrar('2026-12', mesDePrueba('2026-12'));

      (elemento().querySelector('button[aria-label="Mes siguiente"]') as HTMLButtonElement).click();
      await estable();

      expect(url()).toBe('/presupuestos/3/presupuesto/2027-01');
      peticionMes('2027-01').flush(mesDePrueba('2027-01'));
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
      peticionMes('2026-10').flush(mesDePrueba());
      await estable();
    });

    it('ignora la respuesta atrasada de un mes anterior', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');
      const vieja = peticionMes('2026-10');

      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-11');
      expect(vieja.cancelled).toBe(true);
      peticionMes('2026-11').flush(mesDePrueba('2026-11', { listoParaAsignar: 7000 }));
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

      peticionMes('2026-10', true).flush(mesDePrueba());
      await estable();
      expect(texto()).toContain('Facturas');
    });
  });

  describe('estados', () => {
    it('mientras carga muestra el spinner', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');

      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      peticionMes().flush(mesDePrueba());
      await estable();
      expect(elemento().querySelector('mat-progress-spinner')).toBeNull();
    });

    it('un error muestra el aviso con Reintentar, que vuelve a pedir el mes', async () => {
      await harness.navigateByUrl('/presupuestos/3/presupuesto/2026-10');
      peticionMes().flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar el presupuesto del mes.');

      accionAviso.next();
      await estable();
      peticionMes().flush(mesDePrueba());
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
      peticionMes().flush(mesDePrueba());
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

      peticionMes().flush(mesDePrueba());
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
});
