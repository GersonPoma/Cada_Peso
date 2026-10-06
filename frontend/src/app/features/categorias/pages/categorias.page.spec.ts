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
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoCategoriaComponent } from '../components/dialogo-categoria.component';
import { DialogoGrupoComponent } from '../components/dialogo-grupo.component';
import { DialogoMoverCategoriaComponent } from '../components/dialogo-mover-categoria.component';
import { ArbolCategorias } from '../models/arbol-categorias.model';
import { CategoriaResponse } from '../models/categoria-response.model';
import { GrupoCategoriaConCategoriasResponse } from '../models/grupo-categoria-con-categorias-response.model';
import { CategoriasPage } from './categorias.page';

const BASE = '/api/v1/presupuestos/3';

function categoria(
  id: number,
  grupoId: number,
  nombre: string,
  orden: number,
  cambios: Partial<CategoriaResponse> = {},
): CategoriaResponse {
  return {
    id,
    grupoId,
    nombre,
    orden,
    oculta: false,
    nota: null,
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
    ...cambios,
  };
}

function grupo(
  id: number,
  nombre: string,
  orden: number,
  categorias: CategoriaResponse[],
  oculto = false,
): GrupoCategoriaConCategoriasResponse {
  return { id, nombre, orden, oculto, categorias };
}

function arbolInicial(): ArbolCategorias {
  return [
    grupo(1, 'Facturas', 0, [
      categoria(1, 1, 'Alquiler', 0),
      categoria(2, 1, 'Luz', 1, { nota: 'Vence el 10' }),
    ]),
    grupo(3, 'Deseos', 1, [
      categoria(10, 3, 'Restaurantes', 0),
      categoria(11, 3, 'Ocio', 1),
      categoria(12, 3, 'Ropa', 2),
    ]),
    grupo(4, 'Ahorro', 2, [
      categoria(20, 4, 'Fondo de emergencia', 0),
      categoria(21, 4, 'Vacaciones', 1),
    ]),
    grupo(5, 'Mascotas', 3, []),
  ];
}

describe('CategoriasPage', () => {
  let fixture: ComponentFixture<CategoriasPage>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let accionAviso: Subject<void>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => elemento().textContent ?? '';
  const nombresGrupos = () =>
    Array.from(elemento().querySelectorAll('.nombre-grupo')).map((e) => e.textContent?.trim());
  const nombresCategorias = (grupoNombre: string) => {
    const seccion = Array.from(elemento().querySelectorAll('app-grupo-categorias')).find(
      (g) => g.querySelector('.nombre-grupo')?.textContent?.trim() === grupoNombre,
    );
    return Array.from(seccion?.querySelectorAll('.nombre-categoria') ?? []).map((e) =>
      e.textContent?.trim(),
    );
  };
  const botonCon = (contenido: string) =>
    Array.from(elemento().querySelectorAll('button')).find((b) =>
      b.textContent?.includes(contenido),
    ) as HTMLButtonElement | undefined;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function peticionArbol(incluirOcultas = false) {
    return backend.expectOne(
      (p) =>
        p.url === `${BASE}/categorias` && p.params.get('incluirOcultas') === String(incluirOcultas),
    );
  }

  async function responder(arbol: ArbolCategorias = arbolInicial(), ocultas = false) {
    peticionArbol(ocultas).flush(arbol);
    await estable();
  }

  /** Dispara el soltado en la lista de grupos. */
  async function soltarGrupo(desde: number, hasta: number): Promise<void> {
    const arbol = fixture.componentInstance['arbol']() as ArbolCategorias;
    fixture.debugElement.query(By.css('.grupos')).triggerEventHandler('cdkDropListDropped', {
      previousIndex: desde,
      currentIndex: hasta,
      item: { data: { tipo: 'grupo', grupo: arbol[desde] } },
    });
    await estable();
  }

  /** Dispara el soltado de una categoría en la lista del grupo `grupoDestino`. */
  async function soltarCategoria(
    categoriaId: number,
    grupoDestino: number,
    indice: number,
  ): Promise<void> {
    const arbol = fixture.componentInstance['arbol']() as ArbolCategorias;
    const movida = arbol.flatMap((g) => g.categorias).find((c) => c.id === categoriaId);
    const destino = arbol.find((g) => g.id === grupoDestino);
    const indiceGrupo = arbol.findIndex((g) => g.id === grupoDestino);
    fixture.debugElement
      .queryAll(By.css('.categorias'))
      [indiceGrupo].triggerEventHandler('cdkDropListDropped', {
        previousIndex: 0,
        currentIndex: indice,
        item: { data: { tipo: 'categoria', categoria: movida } },
        container: { data: destino },
      });
    await estable();
  }

  function simularDialogo(resultado: unknown) {
    return vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(resultado),
    } as MatDialogRef<unknown, unknown>);
  }

  async function accion(etiquetaMenu: string, item: string): Promise<void> {
    const menu = await cargador.getHarness(
      MatMenuHarness.with({ selector: `[aria-label="${etiquetaMenu}"]` }),
    );
    await menu.open();
    await menu.clickItem({ text: new RegExp(item) });
    await estable();
  }

  beforeEach(async () => {
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
    accionAviso = new Subject<void>();
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockReturnValue({
      onAction: () => accionAviso.asObservable(),
    } as MatSnackBarRef<TextOnlySnackBar>);
    fixture = TestBed.createComponent(CategoriasPage);
    cargador = TestbedHarnessEnvironment.loader(fixture);
    await estable();
  });

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
  });

  describe('carga y estados', () => {
    it('mientras carga muestra el spinner', async () => {
      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      await responder();
    });

    it('pide el árbol sin ocultas y lo muestra en orden con la nota debajo', async () => {
      await responder();

      expect(nombresGrupos()).toEqual(['Facturas', 'Deseos', 'Ahorro', 'Mascotas']);
      expect(nombresCategorias('Deseos')).toEqual(['Restaurantes', 'Ocio', 'Ropa']);
      const luz = Array.from(elemento().querySelectorAll('.categoria')).find((c) =>
        c.textContent?.includes('Luz'),
      );
      expect(luz?.querySelector('.nota')?.textContent).toBe('Vence el 10');
    });

    it('un grupo vacío muestra la zona para soltar', async () => {
      await responder();

      expect(texto()).toContain('Arrastra una categoría aquí');
    });

    it('Mostrar ocultas pide el árbol con incluirOcultas=true y atenúa los ocultos', async () => {
      await responder();

      await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
      await estable();
      await responder(
        [
          ...arbolInicial(),
          grupo(9, 'Viejo', 4, [categoria(90, 9, 'Revistas', 0, { oculta: true })], true),
        ],
        true,
      );

      const viejo = Array.from(elemento().querySelectorAll('.grupo')).find((g) =>
        g.textContent?.includes('Viejo'),
      );
      expect(viejo?.classList.contains('oculto')).toBe(true);
      expect(viejo?.querySelector('.icono-oculto')?.textContent).toBe('visibility_off');
      expect(viejo?.querySelector('.categoria')?.classList.contains('oculta')).toBe(true);
    });

    it('un árbol vacío muestra el estado vacío', async () => {
      await responder([]);

      expect(texto()).toContain('Aún no tienes categorías');
      expect(botonCon('Agregar mi primer grupo')).toBeDefined();
    });

    it('un error muestra el aviso con Reintentar, que vuelve a pedir el árbol', async () => {
      peticionArbol().flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar tus categorías.');

      accionAviso.next();
      await estable();
      await responder();
      expect(nombresGrupos()).toHaveLength(4);
    });

    it('un 401 no avisa', async () => {
      peticionArbol().flush({}, { status: 401, statusText: 'Unauthorized' });
      await estable();

      expect(abrirAviso).not.toHaveBeenCalled();
    });
  });

  describe('diálogos y menús', () => {
    beforeEach(() => responder());

    it('Agregar grupo abre el diálogo de grupo y recarga al crearse', async () => {
      const abrirDialogo = simularDialogo({ id: 6 });

      botonCon('Agregar grupo')?.click();
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoGrupoComponent,
        expect.objectContaining({ data: { modo: 'crear' } }),
      );
      await responder();
    });

    it('Renombrar abre el diálogo con el grupo', async () => {
      const abrirDialogo = simularDialogo(undefined);

      await accion('Acciones del grupo Deseos', 'Renombrar');

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoGrupoComponent,
        expect.objectContaining({
          data: { modo: 'renombrar', grupo: { id: 3, nombre: 'Deseos' } },
        }),
      );
    });

    it('Agregar categoría abre el diálogo de categoría con el grupo', async () => {
      const abrirDialogo = simularDialogo(undefined);
      const botones = Array.from(elemento().querySelectorAll('button')).filter((b) =>
        b.textContent?.includes('Agregar categoría'),
      );

      botones[1].click();
      await estable();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoCategoriaComponent,
        expect.objectContaining({ data: { modo: 'crear', grupoId: 3 } }),
      );
    });

    it('Editar abre el diálogo de categoría con la categoría', async () => {
      const abrirDialogo = simularDialogo(undefined);

      await accion('Acciones de Luz', 'Editar');

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoCategoriaComponent,
        expect.objectContaining({
          data: { modo: 'editar', categoria: arbolInicial()[0].categorias[1] },
        }),
      );
    });

    it('Mover a... abre su diálogo con el árbol y la categoría y recarga al moverse', async () => {
      const abrirDialogo = simularDialogo({ id: 11 });

      await accion('Acciones de Ocio', 'Mover a');

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoMoverCategoriaComponent,
        expect.objectContaining({
          data: { arbol: arbolInicial(), categoria: arbolInicial()[1].categorias[1] },
        }),
      );
      await responder();
    });

    it('Ocultar una categoría llama al endpoint y recarga', async () => {
      await accion('Acciones de Ropa', 'Ocultar');

      backend.expectOne(`${BASE}/categorias/12/ocultar`).flush({});
      await estable();
      await responder();
    });

    it('Ocultar un grupo llama al endpoint y recarga', async () => {
      await accion('Acciones del grupo Ahorro', 'Ocultar');

      backend.expectOne(`${BASE}/grupos-categorias/4/ocultar`).flush({});
      await estable();
      await responder();
    });

    it('Mostrar un grupo oculto llama al endpoint de mostrar', async () => {
      await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
      await estable();
      await responder([...arbolInicial(), grupo(9, 'Viejo', 4, [], true)], true);

      await accion('Acciones del grupo Viejo', 'Mostrar');

      backend.expectOne(`${BASE}/grupos-categorias/9/mostrar`).flush({});
      await estable();
      await responder(arbolInicial(), true);
    });

    it('Mostrar una categoría oculta llama al endpoint de mostrar', async () => {
      await (await cargador.getHarness(MatSlideToggleHarness)).toggle();
      await estable();
      const conOculta = arbolInicial();
      conOculta[0].categorias.push(categoria(3, 1, 'Agua', 2, { oculta: true }));
      await responder(conOculta, true);

      await accion('Acciones de Agua', 'Mostrar');

      backend.expectOne(`${BASE}/categorias/3/mostrar`).flush({});
      await estable();
      await responder(arbolInicial(), true);
    });

    it('un 500 al ocultar muestra el aviso genérico', async () => {
      await accion('Acciones de Ropa', 'Ocultar');

      backend
        .expectOne(`${BASE}/categorias/12/ocultar`)
        .flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
    });
  });

  describe('arrastrar y soltar', () => {
    beforeEach(() => responder());

    it('mover un grupo actualiza la vista antes de la respuesta y envía la posición', async () => {
      await soltarGrupo(0, 2);

      expect(nombresGrupos()).toEqual(['Deseos', 'Ahorro', 'Facturas', 'Mascotas']);
      const peticion = backend.expectOne(`${BASE}/grupos-categorias/1/mover`);
      expect(peticion.request.body).toEqual({ posicion: 2 });
      peticion.flush({});
      await estable();
      await responder();
    });

    it('mover una categoría dentro de su grupo', async () => {
      await soltarCategoria(12, 3, 0);

      expect(nombresCategorias('Deseos')).toEqual(['Ropa', 'Restaurantes', 'Ocio']);
      const peticion = backend.expectOne(`${BASE}/categorias/12/mover`);
      expect(peticion.request.body).toEqual({ grupoId: 3, posicion: 0 });
      peticion.flush({});
      await estable();
      await responder();
    });

    it('mover una categoría a otro grupo, entre dos categorías', async () => {
      await soltarCategoria(11, 4, 1);

      expect(nombresCategorias('Deseos')).toEqual(['Restaurantes', 'Ropa']);
      expect(nombresCategorias('Ahorro')).toEqual(['Fondo de emergencia', 'Ocio', 'Vacaciones']);
      const peticion = backend.expectOne(`${BASE}/categorias/11/mover`);
      expect(peticion.request.body).toEqual({ grupoId: 4, posicion: 1 });
      peticion.flush({});
      await estable();
      await responder();
    });

    it('mover una categoría a un grupo vacío envía la posición 0', async () => {
      await soltarCategoria(11, 5, 0);

      expect(nombresCategorias('Mascotas')).toEqual(['Ocio']);
      const peticion = backend.expectOne(`${BASE}/categorias/11/mover`);
      expect(peticion.request.body).toEqual({ grupoId: 5, posicion: 0 });
      peticion.flush({});
      await estable();
      await responder();
    });

    it('soltar en el mismo lugar no llama al backend', async () => {
      await soltarCategoria(11, 3, 1);
      await soltarGrupo(2, 2);

      backend.expectNone(`${BASE}/categorias/11/mover`);
      backend.expectNone(`${BASE}/grupos-categorias/4/mover`);
    });

    it('un 409 revierte la vista, avisa y recarga', async () => {
      await soltarCategoria(11, 4, 0);
      expect(nombresCategorias('Ahorro')).toContain('Ocio');

      backend
        .expectOne(`${BASE}/categorias/11/mover`)
        .flush({ codigo: 'CATEGORIA_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
      await estable();

      expect(nombresCategorias('Deseos')).toEqual(['Restaurantes', 'Ocio', 'Ropa']);
      expect(nombresCategorias('Ahorro')).toEqual(['Fondo de emergencia', 'Vacaciones']);
      expect(abrirAviso).toHaveBeenCalledWith(
        'Ya hay una categoría con ese nombre en el grupo destino',
        'Cerrar',
        { duration: 6000 },
      );
      await responder();
    });

    it('un 500 al mover un grupo revierte y avisa con el mensaje genérico', async () => {
      await soltarGrupo(0, 1);
      backend
        .expectOne(`${BASE}/grupos-categorias/1/mover`)
        .flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();

      expect(nombresGrupos()).toEqual(['Facturas', 'Deseos', 'Ahorro', 'Mascotas']);
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
        duration: 6000,
      });
      await responder();
    });

    it('con un movimiento en curso se ignora otro soltado', async () => {
      await soltarCategoria(12, 3, 0);
      await soltarGrupo(0, 2);

      const primera = backend.expectOne(`${BASE}/categorias/12/mover`);
      backend.expectNone(`${BASE}/grupos-categorias/1/mover`);
      primera.flush({});
      await estable();
      // Hasta que llega la recarga los arrastres siguen bloqueados.
      await soltarGrupo(0, 2);
      backend.expectNone(`${BASE}/grupos-categorias/1/mover`);
      await responder();

      await soltarGrupo(0, 2);
      backend.expectOne(`${BASE}/grupos-categorias/1/mover`).flush({});
      await estable();
      await responder();
    });
  });
});
