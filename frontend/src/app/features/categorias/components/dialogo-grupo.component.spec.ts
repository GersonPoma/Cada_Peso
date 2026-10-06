import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DatosDialogoGrupo } from '../models/datos-dialogos-categorias.model';
import { GrupoCategoriaResponse } from '../models/grupo-categoria-response.model';
import { DialogoGrupoComponent } from './dialogo-grupo.component';

const URL_GRUPOS = '/api/v1/presupuestos/3/grupos-categorias';

const GRUPO: GrupoCategoriaResponse = {
  id: 4,
  nombre: 'Mascotas',
  orden: 4,
  oculto: false,
  fechaCreacion: '2026-10-06T12:00:00Z',
  fechaActualizacion: '2026-10-06T12:00:00Z',
};

@Component({ template: '' })
class AnfitrionDePrueba {}

describe('DialogoGrupoComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let resultado: GrupoCategoriaResponse | undefined | 'abierto';

  async function abrir(datos: DatosDialogoGrupo): Promise<void> {
    const dialogo = TestBed.inject(MatDialog).open<
      DialogoGrupoComponent,
      DatosDialogoGrupo,
      GrupoCategoriaResponse
    >(DialogoGrupoComponent, { data: datos });
    resultado = 'abierto';
    dialogo.afterClosed().subscribe((valor) => (resultado = valor));
    await estable();
  }

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const nombre = () => cargador.getHarness(MatInputHarness);
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

  it('crear hace POST con el nombre recortado y se cierra con el grupo', async () => {
    await abrir({ modo: 'crear' });
    expect(textoDialogo()).toContain('Nuevo grupo');

    await (await nombre()).setValue('  Mascotas ');
    await (await boton('Crear')).click();
    const peticion = backend.expectOne(URL_GRUPOS);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ nombre: 'Mascotas' });
    peticion.flush(GRUPO);
    await estable();

    expect(resultado).toEqual(GRUPO);
  });

  it('renombrar muestra el nombre actual y hace PUT', async () => {
    await abrir({ modo: 'renombrar', grupo: { id: 4, nombre: 'Deseos' } });
    expect(textoDialogo()).toContain('Renombrar grupo');
    expect(await (await nombre()).getValue()).toBe('Deseos');

    await (await nombre()).setValue('Gustos');
    await (await boton('Guardar')).click();
    const peticion = backend.expectOne(`${URL_GRUPOS}/4`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Gustos' });
    peticion.flush({ ...GRUPO, nombre: 'Gustos' });
    await estable();

    expect(resultado).toEqual({ ...GRUPO, nombre: 'Gustos' });
  });

  it.each([
    ['   ', 'El nombre es obligatorio'],
    ['a'.repeat(101), 'El nombre no puede superar los 100 caracteres'],
  ])('el nombre "%s" muestra su mensaje y deshabilita el botón', async (valor, mensaje) => {
    await abrir({ modo: 'crear' });
    const campo = await nombre();
    await campo.setValue(valor);
    await campo.blur();

    expect(textoDialogo()).toContain(mensaje);
    expect(await (await boton('Crear')).isDisabled()).toBe(true);
  });

  it('deshabilita el botón mientras se envía', async () => {
    await abrir({ modo: 'crear' });
    await (await nombre()).setValue('Mascotas');
    await (await boton('Crear')).click();

    expect(await (await boton('Crear')).isDisabled()).toBe(true);
    backend.expectOne(URL_GRUPOS).flush(GRUPO);
    await estable();
  });

  it('409 GRUPO_CATEGORIA_YA_EXISTE se muestra en el nombre y el diálogo sigue abierto', async () => {
    await abrir({ modo: 'crear' });
    await (await nombre()).setValue('deseos');
    await (await boton('Crear')).click();
    backend
      .expectOne(URL_GRUPOS)
      .flush({ codigo: 'GRUPO_CATEGORIA_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
    await estable();

    expect(textoDialogo()).toContain('Ya tienes un grupo con ese nombre');
    expect(resultado).toBe('abierto');
    expect(abrirAviso).not.toHaveBeenCalled();
  });

  it('400 DATOS_INVALIDOS muestra el mensaje en el nombre', async () => {
    await abrir({ modo: 'crear' });
    await (await nombre()).setValue('Mascotas');
    await (await boton('Crear')).click();
    backend
      .expectOne(URL_GRUPOS)
      .flush(
        { codigo: 'DATOS_INVALIDOS', errores: { nombre: 'Nombre inválido' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await estable();

    expect(textoDialogo()).toContain('Nombre inválido');
    expect(resultado).toBe('abierto');
  });

  it('500 muestra el aviso genérico y el diálogo sigue abierto', async () => {
    await abrir({ modo: 'crear' });
    await (await nombre()).setValue('Mascotas');
    await (await boton('Crear')).click();
    backend.expectOne(URL_GRUPOS).flush({}, { status: 500, statusText: 'Internal Server Error' });
    await estable();

    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    expect(resultado).toBe('abierto');
  });
});
