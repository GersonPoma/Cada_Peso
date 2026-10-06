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
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { CategoriaResponse } from '../models/categoria-response.model';
import { DatosDialogoCategoria } from '../models/datos-dialogos-categorias.model';
import { DialogoCategoriaComponent } from './dialogo-categoria.component';

const URL_CATEGORIAS = '/api/v1/presupuestos/3/categorias';

const LUZ: CategoriaResponse = {
  id: 7,
  grupoId: 1,
  nombre: 'Luz',
  orden: 1,
  oculta: false,
  nota: null,
  fechaCreacion: '2026-10-06T12:00:00Z',
  fechaActualizacion: '2026-10-06T12:00:00Z',
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoCategoriaComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: CategoriaResponse | undefined | 'abierto';

  async function abrir(datos: DatosDialogoCategoria): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoCategoriaComponent,
      DatosDialogoCategoria,
      CategoriaResponse
    >(DialogoCategoriaComponent, { data: datos });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function campo(etiqueta: string): Promise<MatInputHarness> {
    const formField = await cargador.getHarness(
      MatFormFieldHarness.with({ floatingLabelText: etiqueta }),
    );
    return (await formField.getControl(MatInputHarness)) as MatInputHarness;
  }

  const nombre = () => campo('Nombre');
  const nota = () => campo('Nota (opcional)');
  const boton = (texto: string) => cargador.getHarness(MatButtonHarness.with({ text: texto }));
  const textoDialogo = () => document.querySelector('mat-dialog-container')?.textContent ?? '';

  beforeEach(() => {
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
    vi.restoreAllMocks();
  });

  it('crear sin nota hace POST con el grupo, el nombre recortado y nota null', async () => {
    await abrir({ modo: 'crear', grupoId: 3 });
    expect(textoDialogo()).toContain('Nueva categoría');

    await (await nombre()).setValue('  Libros ');
    await (await nota()).setValue('   ');
    await (await boton('Crear')).click();
    const peticion = backend.expectOne(URL_CATEGORIAS);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ grupoId: 3, nombre: 'Libros', nota: null });
    peticion.flush({ ...LUZ, nombre: 'Libros' });
    await estable();

    expect(resultado).toEqual({ ...LUZ, nombre: 'Libros' });
  });

  it('editar muestra nombre y nota y hace PUT con la nota recortada', async () => {
    await abrir({ modo: 'editar', categoria: { ...LUZ, nota: 'Antes' } });
    expect(textoDialogo()).toContain('Editar categoría');
    expect(await (await nombre()).getValue()).toBe('Luz');
    expect(await (await nota()).getValue()).toBe('Antes');

    await (await nota()).setValue('  Vence el 10 ');
    await (await boton('Guardar')).click();
    const peticion = backend.expectOne(`${URL_CATEGORIAS}/7`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Luz', nota: 'Vence el 10' });
    peticion.flush({ ...LUZ, nota: 'Vence el 10' });
    await estable();
  });

  it('borrar la nota al editar envía null', async () => {
    await abrir({ modo: 'editar', categoria: { ...LUZ, nota: 'Antes' } });
    await (await nota()).setValue('');
    await (await boton('Guardar')).click();

    const peticion = backend.expectOne(`${URL_CATEGORIAS}/7`);
    expect(peticion.request.body).toEqual({ nombre: 'Luz', nota: null });
    peticion.flush(LUZ);
    await estable();
  });

  it('una nota de 501 caracteres muestra el mensaje y el contador 501/500', async () => {
    await abrir({ modo: 'crear', grupoId: 3 });
    await (await nombre()).setValue('Libros');
    const campoNota = await nota();
    await campoNota.setValue('a'.repeat(501));
    await campoNota.blur();
    await estable();

    expect(textoDialogo()).toContain('La nota no puede superar los 500 caracteres');
    expect(textoDialogo()).toContain('501/500');
    expect(await (await boton('Crear')).isDisabled()).toBe(true);
  });

  it('el contador arranca en 0/500', async () => {
    await abrir({ modo: 'crear', grupoId: 3 });

    expect(textoDialogo()).toContain('0/500');
  });

  it('un nombre de solo espacios muestra que es obligatorio', async () => {
    await abrir({ modo: 'crear', grupoId: 3 });
    const campoNombre = await nombre();
    await campoNombre.setValue('   ');
    await campoNombre.blur();

    expect(textoDialogo()).toContain('El nombre es obligatorio');
    expect(await (await boton('Crear')).isDisabled()).toBe(true);
  });

  it('deshabilita el botón mientras se envía', async () => {
    await abrir({ modo: 'crear', grupoId: 3 });
    await (await nombre()).setValue('Libros');
    await (await boton('Crear')).click();

    expect(await (await boton('Crear')).isDisabled()).toBe(true);
    backend.expectOne(URL_CATEGORIAS).flush(LUZ);
    await estable();
  });

  it('409 CATEGORIA_YA_EXISTE se muestra en el nombre y el diálogo sigue abierto', async () => {
    await abrir({ modo: 'crear', grupoId: 1 });
    await (await nombre()).setValue('luz');
    await (await boton('Crear')).click();
    backend
      .expectOne(URL_CATEGORIAS)
      .flush({ codigo: 'CATEGORIA_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
    await estable();

    expect(textoDialogo()).toContain('Ya hay una categoría con ese nombre en este grupo');
    expect(resultado).toBe('abierto');
    expect(abrirAviso).not.toHaveBeenCalled();
  });

  it('500 muestra el aviso genérico y el diálogo sigue abierto', async () => {
    await abrir({ modo: 'crear', grupoId: 1 });
    await (await nombre()).setValue('Libros');
    await (await boton('Crear')).click();
    backend
      .expectOne(URL_CATEGORIAS)
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    await estable();

    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    expect(resultado).toBe('abierto');
  });
});
