import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { TestRequest } from '@angular/common/http/testing';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { textoMes } from '../../../shared/fecha/mes';
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

  it('no tiene cabecera propia ni botón de cerrar sesión', async () => {
    backend.expectOne(URL_USUARIO).flush({ nombre: 'Ana' });
    await fixture.whenStable();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('app-cabecera')).toBeNull();
    expect(elemento.querySelector('button')).toBeNull();
    expect(texto()).not.toContain('Cerrar sesión');
    expect(navegar).not.toHaveBeenCalled();
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

  it('consulta el nombre cada vez que se abre: reabierta muestra el nombre cambiado', async () => {
    backend.expectOne(URL_USUARIO).flush({ nombre: 'Ana' });
    await fixture.whenStable();
    expect(texto()).toContain('Hola, Ana');
    fixture.destroy();

    fixture = TestBed.createComponent(InicioPage);
    fixture.detectChanges();
    await fixture.whenStable();
    backend.expectOne(URL_USUARIO).flush({ nombre: 'María José' });
    await fixture.whenStable();

    expect(texto()).toContain('Hola, María José');
  });
});

describe('InicioPage: resumen del presupuesto activo', () => {
  const BASE = '/api/v1/presupuestos/3';
  const ZONA_DE_LA_SUITE = 'America/New_York';
  let fixture: ComponentFixture<InicioPage>;
  let backend: HttpTestingController;
  let activo: PresupuestoActivoService;

  const el = () => fixture.nativeElement as HTMLElement;
  const normal = (texto: string) => texto.replace(/\s+/g, ' ').trim();
  const texto = () => normal(el().textContent ?? '');
  const tarjeta = (clave: string) =>
    el().querySelector(`section[aria-labelledby="titulo-tarjeta-${clave}"]`) as HTMLElement;
  const textoDe = (clave: string) => normal(tarjeta(clave).textContent ?? '');
  const monto = (valor: number) =>
    normal(new Intl.NumberFormat('es-BO', { style: 'currency', currency: 'BOB' }).format(valor));

  const mesCon = (listoParaAsignar: number, ...sobregastadas: boolean[]) => ({
    mes: '2026-10',
    listoParaAsignar,
    grupos: [{ categorias: sobregastadas.map((s, i) => ({ categoriaId: i, sobregastada: s })) }],
  });
  const metas = (...estados: string[]) => ({
    mes: '2026-10',
    totalFaltante: 999000,
    metas: estados.map((estado, i) => ({ categoriaId: i, estado })),
  });
  const CUENTAS = [
    { id: 1, enPresupuesto: true, cerrada: false },
    { id: 2, enPresupuesto: true, cerrada: false },
    { id: 3, enPresupuesto: false, cerrada: false },
  ];
  const SALDOS = [
    { cuentaId: 1, saldo: 1200000 },
    { cuentaId: 2, saldo: -150000 },
    { cuentaId: 3, saldo: 600000 },
  ];

  /** Las respuestas de una importación normal; `cambios` reemplaza lo que cada test necesita. */
  function respuestas(cambios: Record<string, unknown> = {}): Record<string, unknown> {
    return {
      mes: mesCon(13403000, true, false, true),
      cuentas: CUENTAS,
      saldos: SALDOS,
      metas: metas('FALTA', 'FALTA', 'FALTA', 'SOBREGASTADA', 'FINANCIADA', 'POSPUESTA'),
      sinAprobar: { contenido: [], totalElementos: 7 },
      ingresosGastos: { ingresos: 500000, gastos: 265000, neto: 235000 },
      ...cambios,
    };
  }

  function claveDe(peticion: TestRequest): string {
    const url = peticion.request.url;
    if (url === '/api/v1/usuarios/yo') return 'perfil';
    if (url.endsWith('/metas')) return 'metas';
    if (url.includes('/meses/')) return 'mes';
    if (url.endsWith('/cuentas')) return 'cuentas';
    if (url.endsWith('/saldos')) return 'saldos';
    if (url.includes('/reportes/')) return 'ingresosGastos';
    return 'sinAprobar';
  }

  /** Abre Inicio y devuelve las siete peticiones pendientes por nombre. */
  async function abrir(): Promise<Record<string, TestRequest>> {
    fixture = TestBed.createComponent(InicioPage);
    fixture.detectChanges();
    await fixture.whenStable();
    const pendientes = backend.match(() => true);
    return Object.fromEntries(pendientes.map((p) => [claveDe(p), p]));
  }

  async function responder(
    peticiones: Record<string, TestRequest>,
    cambios: Record<string, unknown> = {},
  ): Promise<void> {
    const cuerpos = respuestas(cambios);
    for (const [clave, peticion] of Object.entries(peticiones)) {
      if (clave === 'perfil') {
        peticion.flush({ nombre: 'Ana' });
      } else if (!peticion.cancelled) {
        peticion.flush(cuerpos[clave] as object);
      }
    }
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    process.env['TZ'] = ZONA_DE_LA_SUITE;
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 10, 0, 0));
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
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
    activo = TestBed.inject(PresupuestoActivoService);
    activo.fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    process.env['TZ'] = ZONA_DE_LA_SUITE;
    localStorage.clear();
  });

  describe('saludo y mes', () => {
    it('muestra Hola, <nombre> y el mes actual con el formato de la región', async () => {
      await responder(await abrir());

      expect(texto()).toContain('Hola, Ana');
      expect(texto()).toContain(textoMes('2026-10', 'es-BO'));
    });

    it('usa la fecha local, no la UTC: 31 de octubre 21:00 en Nueva York pide octubre', async () => {
      vi.setSystemTime(new Date(2026, 9, 31, 21, 0, 0));
      const peticiones = await abrir();

      expect(peticiones['mes'].request.url).toBe(`${BASE}/meses/2026-10`);
      await responder(peticiones);
      expect(texto()).toContain(textoMes('2026-10', 'es-BO'));
    });

    it('con otra zona horaria al este de UTC también es el mes local', async () => {
      process.env['TZ'] = 'Asia/Tokyo';
      vi.setSystemTime(new Date(2026, 10, 1, 1, 0, 0));
      const peticiones = await abrir();

      expect(peticiones['mes'].request.url).toBe(`${BASE}/meses/2026-11`);
      await responder(peticiones);
    });
  });

  describe('peticiones', () => {
    it('salen siete a la vez, sin esperar ninguna respuesta', async () => {
      const peticiones = await abrir();

      expect(Object.keys(peticiones).sort()).toEqual(
        ['cuentas', 'ingresosGastos', 'mes', 'metas', 'perfil', 'saldos', 'sinAprobar'].sort(),
      );
      expect(peticiones['mes'].request.urlWithParams).toBe(
        `${BASE}/meses/2026-10?incluirOcultas=true`,
      );
      expect(peticiones['sinAprobar'].request.urlWithParams).toBe(
        `${BASE}/transacciones?soloSinAprobar=true&page=0&size=1`,
      );
      expect(peticiones['ingresosGastos'].request.urlWithParams).toBe(
        `${BASE}/reportes/ingresos-gastos?desde=2026-10&hasta=2026-10`,
      );
      expect(peticiones['metas'].request.url).toBe(`${BASE}/meses/2026-10/metas`);
      await responder(peticiones);
    });

    it('mientras no llegan, cada tarjeta muestra su carga', async () => {
      const peticiones = await abrir();
      fixture.detectChanges();

      expect(el().querySelectorAll('app-tarjeta-resumen mat-progress-spinner')).toHaveLength(6);
      await responder(peticiones);
    });

    it('si responden las demás, una pendiente sigue cargando sola', async () => {
      const peticiones = await abrir();
      const { saldos, ...resto } = peticiones;
      await responder(resto);

      expect(tarjeta('saldo').querySelector('mat-progress-spinner')).not.toBeNull();
      expect(tarjeta('listo').querySelector('mat-progress-spinner')).toBeNull();
      saldos.flush(SALDOS);
    });
  });

  describe('las tarjetas', () => {
    it('Listo para asignar: monto con formato, sin aviso, con enlace al mes', async () => {
      await responder(await abrir());

      expect(textoDe('listo')).toContain(monto(13403));
      expect(textoDe('listo')).not.toContain('Asignaste más de lo que tienes');
      const enlace = tarjeta('listo').querySelector('a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/presupuesto/2026-10');
    });

    it('Listo para asignar negativo: monto negativo y aviso con texto e ícono', async () => {
      await responder(await abrir(), { mes: mesCon(-50000) });

      expect(textoDe('listo')).toContain(monto(-50));
      expect(textoDe('listo')).toContain('Asignaste más de lo que tienes');
      expect(tarjeta('listo').querySelector('mat-icon[aria-hidden="true"]')).not.toBeNull();
    });

    it('Listo para asignar en cero dice que todo está asignado', async () => {
      await responder(await abrir(), { mes: mesCon(0) });

      expect(textoDe('listo')).toContain('Todo el dinero está asignado');
    });

    it('Saldo total: suma abiertas del presupuesto con la tarjeta con su signo (1050000)', async () => {
      await responder(await abrir());

      expect(textoDe('saldo')).toContain(monto(1050));
      const enlace = tarjeta('saldo').querySelector('a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/cuentas');
    });

    it('Categorías sobregastadas: cuenta las marcadas (también ocultas) con ícono', async () => {
      await responder(await abrir());

      expect(textoDe('sobregastadas')).toContain('2 categorías sobregastadas');
      expect(tarjeta('sobregastadas').querySelector('mat-icon')).not.toBeNull();
      const enlace = tarjeta('sobregastadas').querySelector('a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/presupuesto/2026-10');
    });

    it('Categorías sobregastadas: una sola en singular y ninguna sin ícono', async () => {
      await responder(await abrir(), { mes: mesCon(1, true, false) });
      expect(textoDe('sobregastadas')).toContain('1 categoría sobregastada');
      expect(textoDe('sobregastadas')).not.toContain('categorías');
    });

    it('Categorías sobregastadas: ninguna', async () => {
      await responder(await abrir(), { mes: mesCon(1, false) });

      expect(textoDe('sobregastadas')).toContain('Ninguna categoría sobregastada');
      expect(tarjeta('sobregastadas').querySelector('mat-icon')).toBeNull();
    });

    it('Transacciones sin aprobar: el totalElementos y el enlace filtrado', async () => {
      await responder(await abrir());

      expect(textoDe('sin-aprobar')).toContain('7 transacciones sin aprobar');
      const enlace = tarjeta('sin-aprobar').querySelector('a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/transacciones?sinAprobar=1');
    });

    it('Transacciones sin aprobar: cero dice Todo aprobado', async () => {
      await responder(await abrir(), { sinAprobar: { contenido: [], totalElementos: 0 } });

      expect(textoDe('sin-aprobar')).toContain('Todo aprobado');
    });

    it('Metas que faltan: solo las FALTA y nunca el monto de totalFaltante', async () => {
      await responder(await abrir());

      expect(textoDe('metas')).toContain('3 metas por cumplir este mes');
      expect(texto()).not.toContain(monto(999));
    });

    it('Metas que faltan: ninguna pendiente y sin metas', async () => {
      await responder(await abrir(), { metas: metas('FINANCIADA', 'POSPUESTA') });
      expect(textoDe('metas')).toContain('Ninguna meta pendiente');
      fixture.destroy();

      await responder(await abrir(), { metas: metas() });
      expect(textoDe('metas')).toContain('Aún no tienes metas');
    });

    it('Ingresos y gastos: las cifras del backend y el enlace a Reportes', async () => {
      await responder(await abrir());

      expect(textoDe('ingresos-gastos')).toContain(monto(500));
      expect(textoDe('ingresos-gastos')).toContain(monto(265));
      expect(textoDe('ingresos-gastos')).toContain(monto(235));
      const enlace = tarjeta('ingresos-gastos').querySelector('a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe(
        '/presupuestos/3/reportes?reporte=ingresos-gastos&desde=2026-10&hasta=2026-10',
      );
    });

    it('Ingresos y gastos con neto negativo avisa con texto e ícono', async () => {
      await responder(await abrir(), {
        ingresosGastos: { ingresos: 100000, gastos: 140000, neto: -40000 },
      });

      expect(textoDe('ingresos-gastos')).toContain('Gastaste más de lo que ingresó');
      expect(
        tarjeta('ingresos-gastos').querySelector('mat-icon[aria-hidden="true"]'),
      ).not.toBeNull();
    });
  });

  describe('estados vacíos', () => {
    it('presupuesto sin cuentas: aviso con Agregar cuenta y las demás tarjetas siguen', async () => {
      await responder(await abrir(), { cuentas: [], saldos: [] });

      expect(texto()).toContain('Aún no tienes cuentas');
      const enlace = [...el().querySelectorAll('a')].find((a) =>
        a.textContent?.includes('Agregar cuenta'),
      )!;
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/cuentas');
      expect(textoDe('saldo')).toContain('Aún no tienes cuentas');
      expect(textoDe('listo')).toContain(monto(13403));
      expect(textoDe('sobregastadas')).toContain('2 categorías sobregastadas');
    });

    it('mes sin movimientos: texto y Agregar transacción', async () => {
      await responder(await abrir(), {
        ingresosGastos: { ingresos: 0, gastos: 0, neto: 0 },
      });

      expect(textoDe('ingresos-gastos')).toContain('Sin movimientos este mes');
      const enlace = tarjeta('ingresos-gastos').querySelector('a') as HTMLAnchorElement;
      expect(enlace.textContent).toContain('Agregar transacción');
      expect(enlace.getAttribute('href')).toBe('/presupuestos/3/transacciones');
    });

    it('ingresos y gastos que se anulan (neto 0) no son un mes sin movimientos', async () => {
      await responder(await abrir(), {
        ingresosGastos: { ingresos: 500000, gastos: 500000, neto: 0 },
      });

      expect(textoDe('ingresos-gastos')).not.toContain('Sin movimientos este mes');
    });
  });

  describe('errores por tarjeta', () => {
    it('una tarjeta con error no tumba las demás y Reintentar repite solo su petición', async () => {
      const peticiones = await abrir();
      const { metas: fallida, ...resto } = peticiones;
      fallida.flush({}, { status: 500, statusText: 'Server Error' });
      await responder(resto);

      expect(tarjeta('metas').querySelector('[role="alert"]')).not.toBeNull();
      expect(textoDe('listo')).toContain(monto(13403));
      expect(textoDe('sin-aprobar')).toContain('7 transacciones sin aprobar');

      (tarjeta('metas').querySelector('button.reintentar') as HTMLButtonElement).click();
      fixture.detectChanges();
      const reintento = backend.expectOne(`${BASE}/meses/2026-10/metas`);
      backend.expectNone(() => true);
      reintento.flush(metas('FALTA'));
      fixture.detectChanges();

      expect(textoDe('metas')).toContain('1 meta por cumplir este mes');
    });

    it('el error del saldo si falla una de sus dos peticiones', async () => {
      const peticiones = await abrir();
      const { saldos, ...resto } = peticiones;
      saldos.flush({}, { status: 500, statusText: 'Server Error' });
      await responder(resto);

      expect(tarjeta('saldo').querySelector('[role="alert"]')).not.toBeNull();
    });
  });

  describe('respuestas atrasadas y salida de la pantalla', () => {
    it('al cambiar de presupuesto, ninguna tarjeta muestra cifras del anterior', async () => {
      const del3 = await abrir();
      const { perfil, ...sin } = del3;
      perfil.flush({ nombre: 'Ana' });

      activo.fijar({ id: 4, nombre: 'Otro', moneda: 'BOB' });
      fixture.detectChanges();
      await fixture.whenStable();

      // Las seis peticiones del 3 se cancelaron: su respuesta no puede llegar a ninguna tarjeta.
      expect(Object.values(sin).every((p) => p.cancelled)).toBe(true);
      expect(el().querySelectorAll('app-tarjeta-resumen mat-progress-spinner')).toHaveLength(6);
      expect(texto()).not.toContain(monto(13403));

      const del4 = Object.fromEntries(backend.match(() => true).map((p) => [claveDe(p), p]));
      expect(Object.keys(del4)).toHaveLength(6);
      expect(Object.values(del4).every((p) => p.request.url.includes('/presupuestos/4/'))).toBe(
        true,
      );
      await responder(del4, { mes: mesCon(777000, false) });

      expect(textoDe('listo')).toContain(monto(777));
      expect(textoDe('listo')).not.toContain(monto(13403));
      expect(textoDe('sobregastadas')).toContain('Ninguna categoría sobregastada');
    });

    it('al reintentar con la respuesta anterior en camino se muestra la última', async () => {
      const peticiones = await abrir();
      const { metas: primera, ...resto } = peticiones;
      await responder(resto);

      (
        fixture.componentInstance as unknown as { store: { metas: { recargar(): void } } }
      ).store.metas.recargar();
      fixture.detectChanges();
      await fixture.whenStable();
      const segunda = backend.expectOne(`${BASE}/meses/2026-10/metas`);

      expect(primera.cancelled).toBe(true);
      segunda.flush(metas('FALTA', 'FALTA'));
      fixture.detectChanges();
      expect(textoDe('metas')).toContain('2 metas por cumplir este mes');
    });

    it('salir de la pantalla cancela las peticiones pendientes y no actualiza nada', async () => {
      const peticiones = await abrir();

      fixture.destroy();

      expect(Object.values(peticiones).every((p) => p.cancelled)).toBe(true);
      backend.expectNone(() => true);
    });
  });

  describe('accesibilidad', () => {
    it('cada tarjeta es una región con el título como nombre y hay seis', async () => {
      await responder(await abrir());

      const regiones = el().querySelectorAll('section[aria-labelledby^="titulo-tarjeta-"]');
      expect(regiones).toHaveLength(6);
      regiones.forEach((region) => {
        const titulo = el().querySelector(`#${region.getAttribute('aria-labelledby')}`);
        expect(normal(titulo?.textContent ?? '')).not.toBe('');
      });
    });

    it('los enlaces tienen un nombre visible', async () => {
      await responder(await abrir());

      el()
        .querySelectorAll('app-tarjeta-resumen a')
        .forEach((enlace) => expect(normal(enlace.textContent ?? '')).not.toBe(''));
    });
  });
});
