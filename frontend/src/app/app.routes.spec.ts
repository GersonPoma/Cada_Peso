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

    it.each(['/', '/ruta-desconocida', '/presupuestos/3'])('%s termina en /login', async (ruta) => {
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
    const PRESUPUESTOS = [
      {
        id: 3,
        nombre: 'Casa',
        moneda: 'BOB',
        fechaCreacion: '2026-10-06T12:00:00Z',
        fechaActualizacion: '2026-10-06T12:00:00Z',
      },
    ];

    beforeEach(() => iniciar(true));

    /** Responde la redirección, la lista del layout y el usuario de la página de inicio. */
    async function responderHastaInicio(): Promise<void> {
      const backend = TestBed.inject(HttpTestingController);
      const redireccion = backend.expectOne('/api/v1/presupuestos');
      expect(redireccion.request.headers.get('Authorization')).toBe('Bearer abc');
      redireccion.flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      backend.expectOne('/api/v1/usuarios/yo').flush({ nombre: 'Ana' });
      await harness.fixture.whenStable();
      backend.verify();
    }

    it('/ lleva al primer presupuesto, con la cabecera una vez y el saludo', async () => {
      await harness.navigateByUrl('/');
      await responderHastaInicio();

      expect(url()).toBe('/presupuestos/3');
      expect(texto().match(/Cada Peso/g)).toHaveLength(1);
      expect(texto()).toContain('Hola, Ana');
      expect(texto()).toContain('Cerrar sesión');
    });

    it.each(['/login', '/registro'])('%s termina en /presupuestos/3', async (ruta) => {
      await harness.navigateByUrl(ruta);
      await responderHastaInicio();

      expect(url()).toBe('/presupuestos/3');
    });
  });
});
