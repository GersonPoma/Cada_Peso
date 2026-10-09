import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { PerfilResponse } from '../models/perfil-response.model';
import { FormularioNombreComponent } from './formulario-nombre.component';

const URL = '/api/v1/usuarios/yo';

const PERFIL: PerfilResponse = {
  email: 'ana@ejemplo.com',
  rol: 'USUARIO',
  nombre: 'Ana',
  apellido: 'Pérez',
  fechaNacimiento: '2003-03-05',
  telefono: null,
  monedaPredeterminada: 'BOB',
};

describe('FormularioNombreComponent', () => {
  let fixture: ComponentFixture<FormularioNombreComponent>;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let guardados: PerfilResponse[];

  const entrada = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
  const boton = () => fixture.nativeElement.querySelector('button') as HTMLButtonElement;
  const contador = () => fixture.nativeElement.querySelector('.contador')?.textContent?.trim();
  const error = () =>
    (fixture.nativeElement.querySelector('mat-error') as HTMLElement | null)?.textContent?.trim();

  async function escribir(valor: string): Promise<void> {
    entrada().value = valor;
    entrada().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function tocar(): Promise<void> {
    entrada().dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  async function enviar(): Promise<void> {
    boton().click();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...proveerMaterial()],
    });
    backend = TestBed.inject(HttpTestingController);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    guardados = [];
    fixture = TestBed.createComponent(FormularioNombreComponent);
    fixture.componentRef.setInput('nombreActual', 'Ana');
    fixture.componentInstance.guardado.subscribe((perfil) => guardados.push(perfil));
    await fixture.whenStable();
  });

  afterEach(() => {
    backend.verify();
  });

  it('empieza con el nombre actual, el contador y el botón deshabilitado', () => {
    expect(entrada().value).toBe('Ana');
    expect(contador()).toBe('3/100');
    expect(boton().disabled).toBe(true);
  });

  it('envía el nombre recortado con PUT y el cuerpo exacto', async () => {
    await escribir('  María José ');
    await enviar();

    const peticion = backend.expectOne(URL);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'María José' });
    peticion.flush({ ...PERFIL, nombre: 'María José' });
  });

  it('el botón sigue deshabilitado si el nombre es igual al actual, también recortado', async () => {
    await escribir('Ana');
    expect(boton().disabled).toBe(true);

    await escribir('  Ana  ');
    expect(boton().disabled).toBe(true);

    await escribir('Anita');
    expect(boton().disabled).toBe(false);
  });

  it('muestra el mensaje y deshabilita el botón con un nombre vacío o de solo espacios', async () => {
    await escribir('');
    await tocar();
    expect(error()).toBe('El nombre es obligatorio');
    expect(boton().disabled).toBe(true);

    await escribir('    ');
    expect(error()).toBe('El nombre es obligatorio');
    expect(boton().disabled).toBe(true);
  });

  it('mide el largo sin los espacios de los extremos', async () => {
    await escribir('  A  ');
    await tocar();

    expect(error()).toBe('El nombre debe tener al menos 2 caracteres');
    expect(boton().disabled).toBe(true);
  });

  it('rechaza 101 caracteres y acepta 100 aunque lleven espacios alrededor', async () => {
    await escribir('n'.repeat(101));
    await tocar();
    expect(error()).toBe('El nombre no puede superar 100 caracteres');
    expect(boton().disabled).toBe(true);

    await escribir(' ' + 'n'.repeat(100) + ' ');
    expect(error()).toBeUndefined();
    expect(boton().disabled).toBe(false);
  });

  it('el contador muestra el largo ya recortado', async () => {
    await escribir('  María José ');

    expect(contador()).toBe('10/100');
  });

  it('no envía dos veces: un segundo clic antes de la respuesta no repite la petición', async () => {
    await escribir('Anita');

    boton().click();
    boton().click();
    await fixture.whenStable();

    const peticiones = backend.match(URL);
    expect(peticiones).toHaveLength(1);
    expect(boton().disabled).toBe(true);
    peticiones[0].flush({ ...PERFIL, nombre: 'Anita' });
  });

  it('al guardar avisa, emite el perfil nuevo y queda sin cambios al recibir el nombre nuevo', async () => {
    await escribir('Anita');
    await enviar();

    backend.expectOne(URL).flush({ ...PERFIL, nombre: 'Anita' });
    await fixture.whenStable();

    expect(abrirAviso).toHaveBeenCalledWith('Nombre actualizado', 'Cerrar', expect.anything());
    expect(guardados).toEqual([{ ...PERFIL, nombre: 'Anita' }]);

    fixture.componentRef.setInput('nombreActual', 'Anita');
    await fixture.whenStable();

    expect(entrada().value).toBe('Anita');
    expect(boton().disabled).toBe(true);
  });

  describe('errores del servidor', () => {
    it('muestra errores.nombre en el campo, enfoca el campo y bloquea hasta editarlo', async () => {
      await escribir('Anita');
      await enviar();

      backend
        .expectOne(URL)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { nombre: 'Nombre no permitido' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();

      expect(error()).toBe('Nombre no permitido');
      expect(boton().disabled).toBe(true);
      expect(document.activeElement).toBe(entrada());
      expect(abrirAviso).not.toHaveBeenCalled();

      await escribir('Anitaa');

      expect(error()).toBeUndefined();
      expect(boton().disabled).toBe(false);
    });

    it('una clave de errores que no es de ningún campo muestra el aviso genérico', async () => {
      await escribir('Anita');
      await enviar();

      backend
        .expectOne(URL)
        .flush(
          { codigo: 'DATOS_INVALIDOS', errores: { otro: 'x' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', expect.anything());
    });

    it('otro error muestra el aviso genérico, conserva lo escrito y habilita el botón', async () => {
      await escribir('Anita');
      await enviar();

      backend
        .expectOne(URL)
        .flush({ codigo: 'X', detail: 'texto interno' }, { status: 500, statusText: 'Error' });
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', expect.anything());
      expect(fixture.nativeElement.textContent).not.toContain('texto interno');
      expect(entrada().value).toBe('Anita');
      expect(boton().disabled).toBe(false);
    });

    it('un error de red muestra el aviso genérico', async () => {
      await escribir('Anita');
      await enviar();

      backend.expectOne(URL).error(new ProgressEvent('error'));
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', expect.anything());
    });

    it('con un 401 no avisa: lo resuelve el cierre de sesión del interceptor', async () => {
      await escribir('Anita');
      await enviar();

      backend
        .expectOne(URL)
        .flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });
      await fixture.whenStable();

      expect(abrirAviso).not.toHaveBeenCalled();
    });
  });
});
