import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

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

    beforeEach(() => {
      // Hoy, en hora local: el mes por defecto es 2026-10.
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 6, 12));
      return iniciar(true);
    });

    const MES_OCTUBRE = {
      mes: '2026-10',
      listoParaAsignar: 500000,
      totalAsignado: 0,
      totalActividad: 0,
      totalDisponible: 0,
      grupos: [
        {
          id: 1,
          nombre: 'Facturas',
          orden: 0,
          oculto: false,
          categorias: [
            {
              categoriaId: 7,
              nombre: 'Luz',
              oculta: false,
              asignado: 0,
              actividad: 0,
              disponible: 0,
              sobregastada: false,
            },
          ],
        },
      ],
    };

    /** Espera a que la página (cargada en diferido) haga la petición a `url`. */
    async function esperarPeticion(url: string): Promise<TestRequest> {
      const backend = TestBed.inject(HttpTestingController);
      for (let intento = 0; intento < 10; intento++) {
        const [peticion] = backend.match((p) => p.url === url);
        if (peticion) {
          return peticion;
        }
        harness.fixture.detectChanges();
        await harness.fixture.whenStable();
      }
      return backend.expectOne((p) => p.url === url);
    }

    /** Responde la redirección, la lista del layout y el mes de la pantalla principal. */
    async function responderHastaPresupuesto(): Promise<void> {
      const backend = TestBed.inject(HttpTestingController);
      const redireccion = backend.expectOne('/api/v1/presupuestos');
      expect(redireccion.request.headers.get('Authorization')).toBe('Bearer abc');
      redireccion.flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      (await esperarPeticion('/api/v1/presupuestos/3/meses/2026-10')).flush(MES_OCTUBRE);
      // El mes pide sus metas junto con el mes.
      (await esperarPeticion('/api/v1/presupuestos/3/meses/2026-10/metas')).flush({
        mes: '2026-10',
        totalFaltante: 0,
        metas: [],
      });
      await harness.fixture.whenStable();
      backend.verify();
    }

    it('/ lleva al presupuesto del mes actual, con la cabecera una vez', async () => {
      await harness.navigateByUrl('/');
      await responderHastaPresupuesto();

      expect(url()).toBe('/presupuestos/3/presupuesto/2026-10');
      expect(texto().match(/Cada Peso/g)).toHaveLength(1);
      expect(texto()).toContain('Listo para asignar');
      expect(texto()).toContain('Luz');
      expect(texto()).toContain('Cerrar sesión');
    });

    it.each(['/login', '/registro'])('%s termina en el presupuesto del mes', async (ruta) => {
      await harness.navigateByUrl(ruta);
      await responderHastaPresupuesto();

      expect(url()).toBe('/presupuestos/3/presupuesto/2026-10');
    });

    it('/presupuestos/3/inicio saluda con el nombre', async () => {
      await harness.navigateByUrl('/presupuestos/3/inicio');
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      backend.expectOne('/api/v1/usuarios/yo').flush({ nombre: 'Ana' });
      await harness.fixture.whenStable();

      expect(texto()).toContain('Hola, Ana');
    });

    /** Responde la lista del layout y, en la pantalla de cuentas, las cuentas y los saldos. */
    async function responderCuentas(): Promise<void> {
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      backend
        .expectOne((p) => p.url === '/api/v1/presupuestos/3/cuentas')
        .flush([
          {
            id: 5,
            nombre: 'Banco',
            tipo: 'CORRIENTE',
            enPresupuesto: true,
            saldoInicial: 0,
            cerrada: false,
            fechaCreacion: '2026-10-06T12:00:00Z',
            fechaActualizacion: '2026-10-06T12:00:00Z',
          },
        ]);
      backend
        .expectOne('/api/v1/presupuestos/3/transacciones/saldos')
        .flush([{ cuentaId: 5, saldo: 0, saldoConciliado: 0 }]);
      await harness.fixture.whenStable();
      backend.verify();
    }

    const enlaceDelMenu = (etiqueta: string) =>
      Array.from(
        (harness.routeNativeElement as HTMLElement).querySelectorAll('mat-sidenav a'),
      ).find((a) => a.textContent?.includes(etiqueta)) as HTMLAnchorElement | undefined;

    it('/presupuestos/3/cuentas muestra las cuentas con el enlace Cuentas en el menú', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas');
      await responderCuentas();

      expect(url()).toBe('/presupuestos/3/cuentas');
      expect(texto()).toContain('Banco');
      const enlaces = Array.from(
        (harness.routeNativeElement as HTMLElement).querySelectorAll('mat-sidenav a'),
      ).map((a) => a.querySelector('[matListItemTitle]')?.textContent?.trim());
      expect(enlaces).toEqual([
        'Presupuesto',
        'Inicio',
        'Cuentas',
        'Transacciones',
        'Categorías',
        'Beneficiarios',
        'Reportes',
      ]);
    });

    it('/presupuestos/3/cuentas/5/conciliacion se muestra sin entrada en el menú', async () => {
      await harness.navigateByUrl('/presupuestos/3/cuentas/5/conciliacion');
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      (await esperarPeticion('/api/v1/presupuestos/3/cuentas/5')).flush({
        id: 5,
        nombre: 'Banco',
        tipo: 'CORRIENTE',
        enPresupuesto: true,
        cerrada: false,
      });
      backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/categorias').flush([]);
      backend.expectOne('/api/v1/presupuestos/3/cuentas/5/conciliacion').flush([]);
      await harness.fixture.whenStable();
      backend.verify();

      expect(url()).toBe('/presupuestos/3/cuentas/5/conciliacion');
      expect(texto()).toContain('Conciliar Banco');
      expect(texto()).toContain('Aún no has conciliado esta cuenta');
      const enlaces = Array.from(
        (harness.routeNativeElement as HTMLElement).querySelectorAll('mat-sidenav a'),
      ).map((a) => a.querySelector('[matListItemTitle]')?.textContent?.trim());
      expect(enlaces).toEqual([
        'Presupuesto',
        'Inicio',
        'Cuentas',
        'Transacciones',
        'Categorías',
        'Beneficiarios',
        'Reportes',
      ]);
    });

    it('el enlace Cuentas del menú lleva a /presupuestos/3/cuentas', async () => {
      await harness.navigateByUrl('/');
      await responderHastaPresupuesto();

      enlaceDelMenu('Cuentas')?.click();
      await harness.fixture.whenStable();
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/cuentas').flush([]);
      backend.expectOne('/api/v1/presupuestos/3/transacciones/saldos').flush([]);
      await harness.fixture.whenStable();

      expect(url()).toBe('/presupuestos/3/cuentas');
      expect(texto()).toContain('Aún no tienes cuentas');
    });

    it('/presupuestos/3/categorias muestra el árbol con el enlace Categorías', async () => {
      await harness.navigateByUrl('/presupuestos/3/categorias');
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      backend
        .expectOne((p) => p.url === '/api/v1/presupuestos/3/categorias')
        .flush([{ id: 1, nombre: 'Facturas', orden: 0, oculto: false, categorias: [] }]);
      await harness.fixture.whenStable();
      backend.verify();

      expect(url()).toBe('/presupuestos/3/categorias');
      expect(texto()).toContain('Facturas');
      expect(enlaceDelMenu('Categorías')).toBeDefined();
    });

    it('/presupuestos/3/beneficiarios muestra la lista con el enlace Beneficiarios', async () => {
      await harness.navigateByUrl('/presupuestos/3/beneficiarios');
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      (await esperarPeticion('/api/v1/presupuestos/3/beneficiarios')).flush([
        { id: 4, nombre: 'Netflix', categoriaPredeterminadaId: null },
      ]);
      backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/categorias').flush([]);
      await harness.fixture.whenStable();
      backend.verify();

      expect(url()).toBe('/presupuestos/3/beneficiarios');
      expect(texto()).toContain('Netflix');
      expect(enlaceDelMenu('Beneficiarios')).toBeDefined();
    });

    it('/presupuestos/3/transacciones?cuentaId=5 muestra la pantalla filtrada', async () => {
      await harness.navigateByUrl('/presupuestos/3/transacciones?cuentaId=5');
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      (await esperarPeticion('/api/v1/presupuestos/3/cuentas')).flush([
        { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false },
      ]);
      backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/categorias').flush([]);
      backend.expectOne('/api/v1/presupuestos/3/beneficiarios').flush([]);
      const lista = backend.expectOne((p) => p.url === '/api/v1/presupuestos/3/transacciones');
      expect(lista.request.params.get('cuentaId')).toBe('5');
      lista.flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
      backend.expectOne('/api/v1/presupuestos/3/transacciones/saldos').flush([]);
      await harness.fixture.whenStable();
      backend.verify();

      expect(texto()).toContain('Transacciones');
      expect(enlaceDelMenu('Transacciones')).toBeDefined();
    });

    it('/presupuestos/3/reportes muestra el gasto con el enlace Reportes en el menú', async () => {
      await harness.navigateByUrl('/presupuestos/3/reportes');
      const backend = TestBed.inject(HttpTestingController);
      backend.expectOne('/api/v1/presupuestos').flush(PRESUPUESTOS);
      await harness.fixture.whenStable();
      const gasto = await esperarPeticion('/api/v1/presupuestos/3/reportes/gasto-por-categoria');
      expect(gasto.request.params.get('desde')).toBe('2026-05');
      expect(gasto.request.params.get('hasta')).toBe('2026-10');
      gasto.flush({
        desde: '2026-05',
        hasta: '2026-10',
        total: 0,
        grupos: [],
        sinCategoria: { total: 0, porcentaje: 0 },
      });
      await harness.fixture.whenStable();
      backend.verify();

      expect(url()).toBe('/presupuestos/3/reportes?desde=2026-05&hasta=2026-10');
      expect(texto()).toContain('Sin movimientos en este rango');
      expect(enlaceDelMenu('Reportes')).toBeDefined();
    });
  });
});
