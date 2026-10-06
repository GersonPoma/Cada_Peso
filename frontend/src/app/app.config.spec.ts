import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { appConfig } from './app.config';
import { SesionService } from './core/sesion/sesion.service';

describe('appConfig', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let sesion: SesionService;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    // `provideHttpClientTesting()` va al final: cambia el backend pero conserva los interceptores.
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SesionService);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('el interceptor de sesión está conectado: una petición a la API lleva el token', () => {
    sesion.iniciar('abc', '2026-10-07T12:00:00Z');

    http.get('/api/v1/usuarios/yo').subscribe();

    const peticion = backend.expectOne('/api/v1/usuarios/yo');
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer abc');
    peticion.flush({});
  });

  it('un 401 de la API borra la sesión y lleva a /login', () => {
    sesion.iniciar('abc', '2026-10-07T12:00:00Z');

    http.get('/api/v1/usuarios/yo').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/usuarios/yo')
      .flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });

    expect(sesion.haySesion()).toBe(false);
    expect(navegar).toHaveBeenCalledWith('/login');
  });
});
