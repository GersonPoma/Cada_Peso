import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { CLAVE_SESION, SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { InicioPage } from './inicio.page';

const URL_USUARIO = '/api/v1/usuarios/yo';

describe('InicioPage', () => {
  let fixture: ComponentFixture<InicioPage>;
  let backend: HttpTestingController;
  let navegar: ReturnType<typeof vi.spyOn>;
  let abrirAviso: ReturnType<typeof vi.spyOn>;

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

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
    TestBed.inject(SesionService).iniciar('abc', '2099-01-01T00:00:00Z');
    backend = TestBed.inject(HttpTestingController);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture = TestBed.createComponent(InicioPage);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('mientras la petición está pendiente muestra el spinner y no el saludo', () => {
    expect(fixture.nativeElement.querySelector('mat-progress-spinner')).not.toBeNull();
    expect(texto()).not.toContain('Hola');
    backend.expectOne(URL_USUARIO);
  });

  it('al responder muestra Hola, Ana y quita el spinner', async () => {
    backend.expectOne(URL_USUARIO).flush({ nombre: 'Ana' });
    await fixture.whenStable();

    expect(texto()).toContain('Hola, Ana');
    expect(fixture.nativeElement.querySelector('mat-progress-spinner')).toBeNull();
  });

  it('Cerrar sesión, dentro de la cabecera, borra la sesión y navega a /login', async () => {
    backend.expectOne(URL_USUARIO).flush({ nombre: 'Ana' });
    await fixture.whenStable();

    const boton = fixture.nativeElement.querySelector('app-cabecera button') as HTMLButtonElement;
    expect(boton.textContent?.trim()).toBe('Cerrar sesión');
    boton.click();

    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('un 500 muestra el aviso genérico y el texto de error sin cerrar la sesión', async () => {
    backend.expectOne(URL_USUARIO).flush({}, { status: 500, statusText: 'Internal Server Error' });
    await fixture.whenStable();

    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    expect(texto()).toContain('No pudimos cargar tus datos.');
    expect(TestBed.inject(SesionService).haySesion()).toBe(true);
  });

  it('un 401 no muestra ningún aviso', async () => {
    backend
      .expectOne(URL_USUARIO)
      .flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(abrirAviso).not.toHaveBeenCalled();
    expect(texto()).not.toContain('No pudimos cargar tus datos.');
  });
});
