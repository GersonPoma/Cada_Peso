import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SesionService } from '../sesion/sesion.service';
import { sesionInterceptor } from './sesion.interceptor';

describe('sesionInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let sesion: SesionService;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sesionInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
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

  describe('token en las peticiones', () => {
    it('con sesión agrega Authorization: Bearer a una petición a /api/v1', () => {
      sesion.iniciar('abc', '2026-10-07T12:00:00Z');

      http.get('/api/v1/usuarios/yo').subscribe();

      const peticion = backend.expectOne('/api/v1/usuarios/yo');
      expect(peticion.request.headers.get('Authorization')).toBe('Bearer abc');
      peticion.flush({});
    });

    it('sin sesión no agrega el header', () => {
      http.post('/api/v1/auth/login', {}).subscribe();

      const peticion = backend.expectOne('/api/v1/auth/login');
      expect(peticion.request.headers.has('Authorization')).toBe(false);
      peticion.flush({});
    });

    it.each(['/otra/ruta', 'https://ejemplo.com/api/v1/x', '/api/v1x/algo'])(
      'con sesión no agrega el header a %s',
      (url) => {
        sesion.iniciar('abc', '2026-10-07T12:00:00Z');

        http.get(url).subscribe();

        const peticion = backend.expectOne(url);
        expect(peticion.request.headers.has('Authorization')).toBe(false);
        peticion.flush({});
      },
    );
  });

  describe('ante un 401', () => {
    it('en una petición autenticada borra la sesión, lleva a /login y relanza el error', () => {
      sesion.iniciar('abc', '2026-10-07T12:00:00Z');
      let estado: number | undefined;

      http.get('/api/v1/usuarios/yo').subscribe({ error: (e) => (estado = e.status) });
      backend
        .expectOne('/api/v1/usuarios/yo')
        .flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });

      expect(estado).toBe(401);
      expect(sesion.haySesion()).toBe(false);
      expect(localStorage.length).toBe(0);
      expect(navegar).toHaveBeenCalledWith('/login');
    });

    it('en /api/v1/auth/login no cierra la sesión ni redirige', () => {
      sesion.iniciar('abc', '2026-10-07T12:00:00Z');
      let estado: number | undefined;

      http.post('/api/v1/auth/login', {}).subscribe({ error: (e) => (estado = e.status) });
      backend
        .expectOne('/api/v1/auth/login')
        .flush({ codigo: 'CREDENCIALES_INVALIDAS' }, { status: 401, statusText: 'Unauthorized' });

      expect(estado).toBe(401);
      expect(sesion.haySesion()).toBe(true);
      expect(navegar).not.toHaveBeenCalled();
    });

    it('en una URL que no es de la API no cierra la sesión', () => {
      sesion.iniciar('abc', '2026-10-07T12:00:00Z');

      http.get('/otra/ruta').subscribe({ error: () => undefined });
      backend.expectOne('/otra/ruta').flush({}, { status: 401, statusText: 'Unauthorized' });

      expect(sesion.haySesion()).toBe(true);
      expect(navegar).not.toHaveBeenCalled();
    });
  });

  it('ante un 500 conserva la sesión y no redirige', () => {
    sesion.iniciar('abc', '2026-10-07T12:00:00Z');

    http.get('/api/v1/usuarios/yo').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/usuarios/yo')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(sesion.haySesion()).toBe(true);
    expect(navegar).not.toHaveBeenCalled();
  });
});
