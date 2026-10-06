import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { LoginPage } from './login.page';

const RESPUESTA_TOKEN = { token: 'abc', tipo: 'Bearer', expiraEn: '2026-10-07T12:00:00Z' };

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let backend: HttpTestingController;
  let navegar: ReturnType<typeof vi.spyOn>;
  let abrirAviso: ReturnType<typeof vi.spyOn>;

  const elemento = <T extends HTMLElement>(selector: string) =>
    fixture.nativeElement.querySelector(selector) as T;
  const entradaEmail = () => elemento<HTMLInputElement>('input[formControlName="email"]');
  const entradaContrasena = () => elemento<HTMLInputElement>('app-campo-contrasena input');
  const botonEnviar = () => elemento<HTMLButtonElement>('button[type="submit"]');
  const textos = (selector: string) =>
    Array.from(fixture.nativeElement.querySelectorAll(selector) as NodeListOf<HTMLElement>).map(
      (e) => e.textContent?.trim(),
    );

  async function escribir(entrada: HTMLInputElement, valor: string): Promise<void> {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function tocar(entrada: HTMLInputElement): Promise<void> {
    entrada.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  async function completar(email = 'ana@ejemplo.com', contrasena = 'secreta123'): Promise<void> {
    await escribir(entradaEmail(), email);
    await escribir(entradaContrasena(), contrasena);
  }

  async function enviar(): Promise<void> {
    botonEnviar().click();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('muestra las etiquetas en español y la cabecera Cada Peso', () => {
    expect(elemento('mat-card-title').textContent?.trim()).toBe('Iniciar sesión');
    expect(textos('mat-label')).toEqual(['Email', 'Contraseña']);
    expect(botonEnviar().textContent?.trim()).toBe('Iniciar sesión');
    expect(elemento('app-cabecera mat-toolbar').textContent).toContain('Cada Peso');
  });

  it('con los campos tocados y vacíos muestra que son obligatorios y deshabilita el botón', async () => {
    await tocar(entradaEmail());
    await tocar(entradaContrasena());

    expect(textos('mat-error')).toEqual([
      'El email es obligatorio',
      'La contraseña es obligatoria',
    ]);
    expect(botonEnviar().disabled).toBe(true);
  });

  it('no muestra reglas de formato ni de longitud: email abc y contraseña x son válidos', async () => {
    await completar('abc', 'x');
    await tocar(entradaEmail());
    await tocar(entradaContrasena());

    expect(textos('mat-error')).toEqual([]);
    expect(botonEnviar().disabled).toBe(false);
  });

  it('envía el email recortado y en minúsculas y la contraseña sin modificar', async () => {
    await completar('  Ana@Ejemplo.COM ', ' secreta123 ');

    await enviar();

    const peticion = backend.expectOne('/api/v1/auth/login');
    expect(peticion.request.body).toEqual({
      email: 'ana@ejemplo.com',
      contrasena: ' secreta123 ',
    });
    peticion.flush(RESPUESTA_TOKEN);
  });

  it('deshabilita el botón mientras la petición está pendiente', async () => {
    await completar();

    await enviar();

    expect(botonEnviar().disabled).toBe(true);
    backend.expectOne('/api/v1/auth/login').flush(RESPUESTA_TOKEN);
  });

  it('con credenciales correctas guarda la sesión y lleva a /', async () => {
    await completar();
    await enviar();

    backend.expectOne('/api/v1/auth/login').flush(RESPUESTA_TOKEN);
    await fixture.whenStable();

    expect(TestBed.inject(SesionService).token()).toBe('abc');
    expect(navegar).toHaveBeenCalledWith('/');
  });

  it('con CREDENCIALES_INVALIDAS muestra un mensaje general sin marcar ningún campo', async () => {
    await completar('ana@ejemplo.com', 'incorrecta1');
    await enviar();

    backend
      .expectOne('/api/v1/auth/login')
      .flush(
        { codigo: 'CREDENCIALES_INVALIDAS', detail: 'Email o contraseña incorrectos' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    expect(textos('[role="alert"]')).toEqual(['Email o contraseña incorrectos']);
    expect(textos('mat-error')).toEqual([]);
    expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    expect(botonEnviar().disabled).toBe(false);
    expect(navegar).not.toHaveBeenCalled();
    expect(abrirAviso).not.toHaveBeenCalled();
  });

  it('con DATOS_INVALIDOS muestra el mensaje de errores.email en el campo email', async () => {
    await completar();
    await enviar();

    backend
      .expectOne('/api/v1/auth/login')
      .flush(
        { codigo: 'DATOS_INVALIDOS', errores: { email: 'Mensaje del backend' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(textos('mat-error')).toEqual(['Mensaje del backend']);
    expect(abrirAviso).not.toHaveBeenCalled();
  });

  it.each([
    [
      'un 500',
      (peticion: { flush: Function }) =>
        peticion.flush({}, { status: 500, statusText: 'Internal Server Error' }),
    ],
    [
      'un error de red',
      (peticion: { error: Function }) => peticion.error(new ProgressEvent('error')),
    ],
  ])('ante %s muestra el aviso genérico y vuelve a habilitar el botón', async (_caso, fallar) => {
    await completar();
    await enviar();

    fallar(backend.expectOne('/api/v1/auth/login') as never);
    await fixture.whenStable();

    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    expect(botonEnviar().disabled).toBe(false);
  });

  it('ofrece un enlace a /registro', () => {
    const enlace = elemento<HTMLAnchorElement>('a');

    expect(enlace.textContent?.trim()).toBe('Regístrate');
    expect(enlace.getAttribute('href')).toBe('/registro');
  });
});
