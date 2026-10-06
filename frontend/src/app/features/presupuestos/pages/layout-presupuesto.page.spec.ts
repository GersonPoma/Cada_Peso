import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSidenavHarness } from '@angular/material/sidenav/testing';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { CLAVE_SESION, SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoPresupuestoComponent } from '../components/dialogo-presupuesto.component';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { LayoutPresupuestoPage } from './layout-presupuesto.page';

const URL_PRESUPUESTOS = '/api/v1/presupuestos';

function presupuesto(id: number, nombre: string, moneda = 'BOB'): PresupuestoResponse {
  return {
    id,
    nombre,
    moneda,
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
  };
}

const CASA = presupuesto(3, 'Casa', 'BOB');
const VIAJES = presupuesto(1, 'Viajes', 'USD');

@Component({ template: '<p class="seccion-de-prueba">Contenido de la sección</p>' })
class SeccionDePrueba {}

@Component({ template: '<p>Raíz</p>' })
class RaizDePrueba {}

describe('LayoutPresupuestoPage', () => {
  let harness: RouterTestingHarness;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let presupuestoActivo: PresupuestoActivoService;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let accionAviso: Subject<void>;
  let pantalla: BehaviorSubject<BreakpointState>;

  const elemento = () => harness.routeNativeElement as HTMLElement;
  const texto = () => elemento().textContent ?? '';
  const url = () => TestBed.inject(Router).url;
  const botonCon = (contenido: string) =>
    Array.from(elemento().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === contenido,
    ) as HTMLButtonElement | undefined;

  async function estable(): Promise<void> {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
  }

  /** Entra a la URL y responde la lista de presupuestos. */
  async function entrar(ruta: string, lista = [CASA, VIAJES]): Promise<void> {
    await harness.navigateByUrl(ruta);
    backend.expectOne(URL_PRESUPUESTOS).flush(lista);
    await estable();
  }

  function simularDialogo(resultado: PresupuestoResponse | undefined) {
    return vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(resultado),
    } as MatDialogRef<unknown, unknown>);
  }

  async function abrirGestion(): Promise<MatMenuHarness> {
    const menu = await cargador.getHarness(MatMenuHarness);
    await menu.open();
    return menu;
  }

  beforeEach(async () => {
    localStorage.clear();
    pantalla = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', component: RaizDePrueba },
          { path: 'login', component: RaizDePrueba },
          {
            path: 'presupuestos/:presupuestoId',
            component: LayoutPresupuestoPage,
            children: [
              {
                path: '',
                component: SeccionDePrueba,
                data: { seccion: { etiqueta: 'Inicio', icono: 'home' } },
              },
            ],
          },
        ]),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: BreakpointObserver, useValue: { observe: () => pantalla.asObservable() } },
      ],
    });
    TestBed.inject(SesionService).iniciar('abc', '2099-01-01T00:00:00Z');
    backend = TestBed.inject(HttpTestingController);
    presupuestoActivo = TestBed.inject(PresupuestoActivoService);
    accionAviso = new Subject<void>();
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockReturnValue({
      onAction: () => accionAviso.asObservable(),
    } as MatSnackBarRef<TextOnlySnackBar>);
    harness = await RouterTestingHarness.create();
    cargador = TestbedHarnessEnvironment.loader(harness.fixture);
  });

  afterEach(() => {
    backend.verify();
    TestBed.inject(MatDialog).closeAll();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  describe('presupuesto activo según la URL', () => {
    it('fija el presupuesto de la URL con su id, nombre y moneda', async () => {
      await entrar('/presupuestos/1');

      expect(presupuestoActivo.presupuesto()).toEqual({ id: 1, nombre: 'Viajes', moneda: 'USD' });
      expect(url()).toBe('/presupuestos/1');
    });

    it('muestra la cabecera una vez y el contenido de la sección', async () => {
      await entrar('/presupuestos/3');

      expect(elemento().querySelectorAll('app-cabecera')).toHaveLength(1);
      expect(texto()).toContain('Cada Peso');
      expect(elemento().querySelector('.seccion-de-prueba')).not.toBeNull();
    });

    it('mientras carga la lista muestra el spinner y no pinta la sección', async () => {
      await harness.navigateByUrl('/presupuestos/3');

      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      expect(elemento().querySelector('.seccion-de-prueba')).toBeNull();
      expect(presupuestoActivo.presupuesto()).toBeNull();
      backend.expectOne(URL_PRESUPUESTOS).flush([CASA]);
      await estable();
    });

    it.each(['99', 'abc'])('un id %s que no está en la lista vuelve a /', async (id) => {
      await entrar(`/presupuestos/${id}`);

      expect(url()).toBe('/');
      expect(presupuestoActivo.presupuesto()).toBeNull();
    });
  });

  describe('selector de presupuesto', () => {
    beforeEach(() => entrar('/presupuestos/3'));

    it('lista todos los presupuestos con el activo seleccionado', async () => {
      const selector = await cargador.getHarness(MatSelectHarness);
      await selector.open();
      const opciones = await Promise.all((await selector.getOptions()).map((o) => o.getText()));

      expect(opciones).toEqual(['Casa', 'Viajes']);
      expect(await selector.getValueText()).toBe('Casa');
    });

    it('elegir otro lleva a su URL y cambia el presupuesto activo', async () => {
      const selector = await cargador.getHarness(MatSelectHarness);
      await selector.clickOptions({ text: 'Viajes' });
      await estable();

      expect(url()).toBe('/presupuestos/1');
      expect(presupuestoActivo.presupuesto()).toEqual({ id: 1, nombre: 'Viajes', moneda: 'USD' });
      expect(await selector.getValueText()).toBe('Viajes');
    });
  });

  describe('gestionar presupuestos', () => {
    beforeEach(() => entrar('/presupuestos/3'));

    it('Nuevo presupuesto abre el diálogo de crear, agrega el nuevo y lleva a él', async () => {
      const abrirDialogo = simularDialogo(presupuesto(9, 'Ahorro', 'USD'));

      await (await abrirGestion()).clickItem({ text: /Nuevo presupuesto/ });
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoPresupuestoComponent,
        expect.objectContaining({ data: { modo: 'crear' } }),
      );
      expect(url()).toBe('/presupuestos/9');
      expect(presupuestoActivo.presupuesto()).toEqual({ id: 9, nombre: 'Ahorro', moneda: 'USD' });
      const selector = await cargador.getHarness(MatSelectHarness);
      await selector.open();
      const opciones = await Promise.all((await selector.getOptions()).map((o) => o.getText()));
      expect(opciones).toEqual(['Ahorro', 'Casa', 'Viajes']);
    });

    it('Renombrar abre el diálogo con el activo y actualiza el selector y el activo', async () => {
      const abrirDialogo = simularDialogo({ ...CASA, nombre: 'Hogar' });

      await (await abrirGestion()).clickItem({ text: /Renombrar presupuesto/ });
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoPresupuestoComponent,
        expect.objectContaining({
          data: { modo: 'renombrar', presupuesto: { id: 3, nombre: 'Casa' } },
        }),
      );
      expect(url()).toBe('/presupuestos/3');
      expect(presupuestoActivo.presupuesto()).toEqual({ id: 3, nombre: 'Hogar', moneda: 'BOB' });
      expect(await (await cargador.getHarness(MatSelectHarness)).getValueText()).toBe('Hogar');
    });

    it('cancelar el diálogo no cambia nada', async () => {
      simularDialogo(undefined);

      await (await abrirGestion()).clickItem({ text: /Nuevo presupuesto/ });
      await estable();

      expect(url()).toBe('/presupuestos/3');
      expect(presupuestoActivo.presupuesto()?.id).toBe(3);
    });
  });

  describe('error al cargar la lista', () => {
    beforeEach(async () => {
      await harness.navigateByUrl('/presupuestos/3');
      backend
        .expectOne(URL_PRESUPUESTOS)
        .flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();
    });

    it('muestra el aviso genérico con Reintentar y no pinta la sección', () => {
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar tus presupuestos.');
      expect(elemento().querySelector('.seccion-de-prueba')).toBeNull();
    });

    it('Reintentar vuelve a pedir la lista y fija el presupuesto', async () => {
      accionAviso.next();
      backend.expectOne(URL_PRESUPUESTOS).flush([CASA]);
      await estable();

      expect(presupuestoActivo.presupuesto()?.id).toBe(3);
      expect(elemento().querySelector('.seccion-de-prueba')).not.toBeNull();
    });
  });

  it('Cerrar sesión deja sin sesión ni presupuesto activo y lleva a /login', async () => {
    await entrar('/presupuestos/3');

    botonCon('Cerrar sesión')?.click();
    await estable();

    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    expect(presupuestoActivo.presupuesto()).toBeNull();
    expect(url()).toBe('/login');
  });

  describe('menú lateral', () => {
    it('en pantalla ancha está abierto, en modo side y sin botón de menú', async () => {
      await entrar('/presupuestos/3');
      const menu = await cargador.getHarness(MatSidenavHarness);

      expect(await menu.isOpen()).toBe(true);
      expect(await menu.getMode()).toBe('side');
      expect(elemento().querySelector('button[aria-label="Abrir menú"]')).toBeNull();
      expect(elemento().querySelector('mat-sidenav')?.textContent).toContain('Inicio');
    });

    it('en pantalla estrecha se abre con el botón y se cierra al elegir una sección', async () => {
      pantalla.next({ matches: true, breakpoints: {} });
      await entrar('/presupuestos/3');
      const menu = await cargador.getHarness(MatSidenavHarness);

      expect(await menu.isOpen()).toBe(false);
      expect(await menu.getMode()).toBe('over');

      (elemento().querySelector('button[aria-label="Abrir menú"]') as HTMLButtonElement).click();
      await estable();
      expect(await menu.isOpen()).toBe(true);

      (elemento().querySelector('mat-sidenav a') as HTMLAnchorElement).click();
      await estable();
      expect(await menu.isOpen()).toBe(false);
    });
  });
});
