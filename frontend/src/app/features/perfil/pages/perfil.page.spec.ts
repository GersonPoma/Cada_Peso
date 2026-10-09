import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { sesionInterceptor } from '../../../core/auth/sesion.interceptor';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { SesionService } from '../../../core/sesion/sesion.service';
import { PerfilResponse } from '../models/perfil-response.model';
import { PerfilPage } from './perfil.page';

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

describe('PerfilPage', () => {
  let fixture: ComponentFixture<PerfilPage>;
  let backend: HttpTestingController;

  const host = () => fixture.nativeElement as HTMLElement;
  const texto = () => host().textContent ?? '';
  const botones = () => Array.from(host().querySelectorAll('button'));

  async function crear(): Promise<void> {
    fixture = TestBed.createComponent(PerfilPage);
    await fixture.whenStable();
  }

  async function cargar(perfil: PerfilResponse = PERFIL): Promise<void> {
    await crear();
    backend.expectOne(URL).flush(perfil);
    await fixture.whenStable();
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sesionInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).iniciar('abc', new Date(Date.now() + 3_600_000).toISOString());
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('muestra el título y, mientras carga, un indicador y ningún formulario', async () => {
    await crear();

    expect(host().querySelector('h1')?.textContent).toContain('Mi perfil');
    expect(host().querySelector('mat-progress-spinner')).not.toBeNull();
    expect(host().querySelector('form')).toBeNull();
    backend.expectOne(URL).flush(PERFIL);
  });

  it('con la respuesta muestra los datos, el formulario de nombre y el de contraseña', async () => {
    await cargar();

    expect(host().querySelector('mat-progress-spinner')).toBeNull();
    expect(texto()).toContain('ana@ejemplo.com');
    expect(texto()).toContain('Pérez');
    expect(texto()).toContain('BOB · ');
    expect(texto()).toContain('No registrado');
    expect((host().querySelector('app-formulario-nombre input') as HTMLInputElement).value).toBe(
      'Ana',
    );
    expect(host().querySelector('app-formulario-contrasena')).not.toBeNull();
  });

  it('los únicos campos editables son el nombre y las tres contraseñas', async () => {
    await cargar();

    const editables = Array.from(host().querySelectorAll('input')).filter(
      (campo) => !campo.readOnly,
    );

    expect(editables).toHaveLength(4);
    expect(editables.map((campo) => campo.getAttribute('autocomplete'))).toEqual([
      'given-name',
      'current-password',
      'new-password',
      'new-password',
    ]);
  });

  it('no repite el botón Cerrar sesión', async () => {
    await cargar();

    expect(botones().map((boton) => boton.textContent?.trim())).not.toContain('Cerrar sesión');
    expect(texto()).not.toContain('Cerrar sesión');
  });

  it('apila las tres secciones en una sola columna, cada una con su título', async () => {
    await cargar();

    const secciones = host().querySelector('.secciones') as HTMLElement;
    const tarjetas = Array.from(secciones.children);
    expect(tarjetas.map((tarjeta) => tarjeta.tagName.toLowerCase())).toEqual([
      'mat-card',
      'mat-card',
      'mat-card',
    ]);
    expect(tarjetas.map((tarjeta) => tarjeta.querySelector('h2')?.textContent?.trim())).toEqual([
      'Mis datos',
      'Cambiar nombre',
      'Cambiar contraseña',
    ]);
    for (const tarjeta of tarjetas) {
      const titulo = tarjeta.getAttribute('aria-labelledby');
      expect(host().querySelector(`#${titulo}`)).not.toBeNull();
    }
  });

  it('un error muestra el aviso anunciado y Reintentar repite la petición', async () => {
    await crear();
    backend.expectOne(URL).flush({ codigo: 'ERROR' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(host().querySelector('[role="alert"]')?.textContent).toContain(
      'No pudimos cargar tus datos.',
    );
    expect(TestBed.inject(SesionService).haySesion()).toBe(true);

    botones()
      .find((boton) => boton.textContent?.includes('Reintentar'))
      ?.click();
    await fixture.whenStable();
    backend.expectOne(URL).flush(PERFIL);
    await fixture.whenStable();

    expect(host().querySelector('[role="alert"]')).toBeNull();
    expect(texto()).toContain('ana@ejemplo.com');
  });

  it('con un 401 el interceptor cierra la sesión y la pantalla no muestra un aviso', async () => {
    await crear();

    backend.expectOne(URL).flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'No' });
    await fixture.whenStable();

    expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    expect(host().querySelector('[role="alert"]')).toBeNull();
  });

  it('al guardar el nombre, la pantalla muestra el nombre nuevo sin recargar', async () => {
    await cargar();
    const entrada = host().querySelector('app-formulario-nombre input') as HTMLInputElement;
    entrada.value = 'María José';
    entrada.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    (host().querySelector('app-formulario-nombre button') as HTMLButtonElement).click();
    await fixture.whenStable();
    backend.expectOne(URL).flush({ ...PERFIL, nombre: 'María José' });
    await fixture.whenStable();

    expect(entrada.value).toBe('María José');
    expect(
      (host().querySelector('app-formulario-nombre button') as HTMLButtonElement).disabled,
    ).toBe(true);
    backend.expectNone(URL);
  });
});
