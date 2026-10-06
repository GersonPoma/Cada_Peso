import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { appConfig } from './app.config';
import { SesionService } from './core/sesion/sesion.service';

describe('rutas', () => {
  let harness: RouterTestingHarness;

  const texto = () => (harness.routeNativeElement as HTMLElement).textContent ?? '';
  const url = () => TestBed.inject(Router).url;

  async function iniciar(conSesion: boolean): Promise<void> {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    if (conSesion) {
      TestBed.inject(SesionService).iniciar('abc', '2099-01-01T00:00:00Z');
    }
    harness = await RouterTestingHarness.create();
  }

  afterEach(() => localStorage.clear());

  describe('sin sesión', () => {
    beforeEach(() => iniciar(false));

    it.each(['/', '/ruta-desconocida'])('%s termina en /login', async (ruta) => {
      await harness.navigateByUrl(ruta);

      expect(url()).toBe('/login');
      expect(texto()).toContain('Iniciar sesión');
      expect(texto()).toContain('Cada Peso');
    });

    it('/registro se muestra sin redirección y con la cabecera', async () => {
      await harness.navigateByUrl('/registro');

      expect(url()).toBe('/registro');
      expect(texto()).toContain('Crear cuenta');
      expect(texto()).toContain('Cada Peso');
    });
  });

  describe('con sesión', () => {
    beforeEach(() => iniciar(true));

    it('/ muestra la página de inicio con la cabecera', async () => {
      await harness.navigateByUrl('/');
      const peticion = TestBed.inject(HttpTestingController).expectOne('/api/v1/usuarios/yo');
      expect(peticion.request.headers.get('Authorization')).toBe('Bearer abc');
      peticion.flush({ nombre: 'Ana' });
      await harness.fixture.whenStable();

      expect(url()).toBe('/');
      expect(texto()).toContain('Cada Peso');
      expect(texto()).toContain('Hola, Ana');
    });

    it.each(['/login', '/registro'])('%s termina en /', async (ruta) => {
      await harness.navigateByUrl(ruta);
      TestBed.inject(HttpTestingController)
        .expectOne('/api/v1/usuarios/yo')
        .flush({ nombre: 'Ana' });

      expect(url()).toBe('/');
    });
  });
});
