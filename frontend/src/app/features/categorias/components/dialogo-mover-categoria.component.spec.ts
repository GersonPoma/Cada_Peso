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
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { ArbolCategorias } from '../models/arbol-categorias.model';
import { CategoriaResponse } from '../models/categoria-response.model';
import { DatosDialogoMoverCategoria } from '../models/datos-dialogos-categorias.model';
import { DialogoMoverCategoriaComponent } from './dialogo-mover-categoria.component';

function categoria(id: number, grupoId: number, nombre: string, orden: number): CategoriaResponse {
  return {
    id,
    grupoId,
    nombre,
    orden,
    oculta: false,
    nota: null,
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
  };
}

const OCIO = categoria(11, 3, 'Ocio', 1);
const ARBOL: ArbolCategorias = [
  {
    id: 3,
    nombre: 'Deseos',
    orden: 0,
    oculto: false,
    categorias: [categoria(10, 3, 'Restaurantes', 0), OCIO, categoria(12, 3, 'Ropa', 2)],
  },
  {
    id: 4,
    nombre: 'Ahorro',
    orden: 1,
    oculto: false,
    categorias: [categoria(20, 4, 'Fondo de emergencia', 0), categoria(21, 4, 'Vacaciones', 1)],
  },
  { id: 5, nombre: 'Mascotas', orden: 2, oculto: false, categorias: [] },
];

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoMoverCategoriaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let resultado: CategoriaResponse | undefined | 'abierto';

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const selector = async (etiqueta: string) => {
    const formField = await cargador.getHarness(
      MatFormFieldHarness.with({ floatingLabelText: etiqueta }),
    );
    return (await formField.getControl(MatSelectHarness)) as MatSelectHarness;
  };

  async function elegir(grupo: string, lugar: string): Promise<void> {
    await (await selector('Grupo')).clickOptions({ text: grupo });
    await (await selector('Lugar')).clickOptions({ text: lugar });
    await (await cargador.getHarness(MatButtonHarness.with({ text: 'Mover' }))).click();
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
    vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoMoverCategoriaComponent,
      DatosDialogoMoverCategoria,
      CategoriaResponse
    >(DialogoMoverCategoriaComponent, { data: { arbol: ARBOL, categoria: OCIO } });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  });

  afterEach(() => {
    backend.verify();
    TestBed.inject(MatDialog).closeAll();
    vi.restoreAllMocks();
  });

  it('arranca en el grupo actual y ofrece los lugares sin la propia categoría', async () => {
    const lugar = await selector('Lugar');
    await lugar.open();
    const opciones = await Promise.all((await lugar.getOptions()).map((o) => o.getText()));

    expect(await (await selector('Grupo')).getValueText()).toBe('Deseos');
    expect(opciones).toEqual(['Al principio', 'Después de Restaurantes', 'Después de Ropa']);
  });

  it('al principio de otro grupo envía la posición 0', async () => {
    await elegir('Ahorro', 'Al principio');

    const peticion = backend.expectOne('/api/v1/presupuestos/3/categorias/11/mover');
    expect(peticion.request.body).toEqual({ grupoId: 4, posicion: 0 });
    peticion.flush({ ...OCIO, grupoId: 4, orden: 0 });
    await estable();
    expect(resultado).toEqual({ ...OCIO, grupoId: 4, orden: 0 });
  });

  it('después de la última de otro grupo envía m', async () => {
    await elegir('Ahorro', 'Después de Vacaciones');

    const peticion = backend.expectOne('/api/v1/presupuestos/3/categorias/11/mover');
    expect(peticion.request.body).toEqual({ grupoId: 4, posicion: 2 });
    peticion.flush(OCIO);
    await estable();
  });

  it('a un grupo vacío envía la posición 0', async () => {
    await elegir('Mascotas', 'Al principio');

    const peticion = backend.expectOne('/api/v1/presupuestos/3/categorias/11/mover');
    expect(peticion.request.body).toEqual({ grupoId: 5, posicion: 0 });
    peticion.flush(OCIO);
    await estable();
  });

  it('dentro del mismo grupo, después de Ropa envía la posición 2', async () => {
    await elegir('Deseos', 'Después de Ropa');

    const peticion = backend.expectOne('/api/v1/presupuestos/3/categorias/11/mover');
    expect(peticion.request.body).toEqual({ grupoId: 3, posicion: 2 });
    peticion.flush(OCIO);
    await estable();
  });

  it('una elección que no cambia nada cierra sin llamar al backend', async () => {
    await elegir('Deseos', 'Después de Restaurantes');
    await estable();

    backend.expectNone('/api/v1/presupuestos/3/categorias/11/mover');
    expect(resultado).toBeUndefined();
  });

  it('409 CATEGORIA_YA_EXISTE se muestra en el diálogo', async () => {
    await elegir('Ahorro', 'Al principio');
    backend
      .expectOne('/api/v1/presupuestos/3/categorias/11/mover')
      .flush({ codigo: 'CATEGORIA_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
    await estable();

    expect(document.querySelector('mat-dialog-container [role="alert"]')?.textContent).toContain(
      'Ya hay una categoría con ese nombre en el grupo destino',
    );
    expect(resultado).toBe('abierto');
  });
});
