import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoConfirmarBorradoComponent } from '../components/dialogo-confirmar-borrado.component';
import { DialogoProgramadaComponent } from '../components/dialogo-programada.component';
import { TransaccionProgramadaResponse } from '../models/transaccion-programada-response.model';
import {
  MENSAJE_BORRADA,
  MENSAJE_ERROR_CARGA,
  MENSAJE_PAUSADA,
  MENSAJE_REANUDADA,
  MENSAJE_REFERENCIA_INEXISTENTE,
  MENSAJE_SIN_PROGRAMADAS,
  PREFIJO_ULTIMO_ERROR,
  SUGERENCIA_ULTIMO_ERROR,
} from '../services/mensajes-programada';
import { ProgramadasPage } from './programadas.page';

const BASE = '/api/v1/presupuestos/3';
const URL = `${BASE}/transacciones-programadas`;

const CUENTAS = [{ id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false }];
const GRUPOS = [
  {
    id: 1,
    nombre: 'Casa',
    oculto: false,
    categorias: [{ id: 7, nombre: 'Alquiler', oculta: false, esPagoTarjeta: false }],
  },
];

function programada(
  cambios: Partial<TransaccionProgramadaResponse> = {},
): TransaccionProgramadaResponse {
  return {
    id: 1,
    cuentaId: 5,
    fechaInicio: '2026-01-05',
    frecuencia: 'MENSUAL',
    fechaFin: null,
    monto: -150000,
    categoriaId: 7,
    beneficiario: 'Inmobiliaria',
    memo: null,
    activa: true,
    proximaFecha: '2026-11-05',
    ultimoError: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}

const normalizar = (texto: string | null | undefined) =>
  (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

describe('ProgramadasPage', { timeout: 30000 }, () => {
  let fixture: ComponentFixture<ProgramadasPage>;
  let backend: HttpTestingController;
  let cargador: HarnessLoader;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let abrirDialogo: ReturnType<typeof vi.spyOn>;
  let resultadoDialogo: unknown;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);
  const filas = () => Array.from(elemento().querySelectorAll('li.programada')) as HTMLElement[];
  const boton = (t: string) =>
    Array.from(elemento().querySelectorAll('button')).find((b) =>
      normalizar(b.textContent).endsWith(t),
    ) as HTMLButtonElement;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function responderCarga(programadas: TransaccionProgramadaResponse[]): void {
    backend.expectOne(URL).flush(programadas);
    backend.expectOne((p) => p.url === `${BASE}/cuentas`).flush(CUENTAS);
    backend.expectOne((p) => p.url === `${BASE}/categorias`).flush(GRUPOS);
  }

  async function iniciar(programadas = [programada()]): Promise<void> {
    fixture = TestBed.createComponent(ProgramadasPage);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
    await estable();
    responderCarga(programadas);
    await estable();
  }

  async function menu(indice: number, accion: RegExp): Promise<void> {
    const disparador = filas()[indice].querySelector('button') as HTMLButtonElement;
    disparador.click();
    await estable();
    const harness = await cargador.getHarness(MatMenuHarness);
    await harness.clickItem({ text: accion });
    await estable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    resultadoDialogo = undefined;
    abrirDialogo = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockImplementation(() => ({ afterClosed: () => of(resultadoDialogo) }) as never);
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('muestra cuenta, beneficiario, categoría, monto, frecuencia y estado', async () => {
    await iniciar();

    const fila = normalizar(filas()[0].textContent);
    expect(fila).toContain('Inmobiliaria');
    expect(fila).toContain('Banco · Alquiler');
    expect(fila).toContain('Salida');
    expect(fila).toContain('Cada mes');
    expect(fila).toContain('5 de noviembre de 2026');
    expect(fila).toContain('Activa');
  });

  it('una finalizada dice Finalizada, sin próxima fecha y con su fecha de fin', async () => {
    await iniciar([programada({ proximaFecha: null, fechaFin: '2026-09-15' })]);

    const fila = normalizar(filas()[0].textContent);
    expect(fila).toContain('Finalizada');
    expect(fila).toContain('Próxima fecha —');
    expect(fila).toContain('Cada mes hasta 15 de septiembre de 2026');
  });

  it('una con error muestra el motivo y la sugerencia', async () => {
    await iniciar([programada({ ultimoError: 'La cuenta está cerrada' })]);

    const fila = normalizar(filas()[0].textContent);
    expect(fila).toContain('No se pudo generar');
    expect(fila).toContain(`${PREFIJO_ULTIMO_ERROR} La cuenta está cerrada`);
    expect(fila).toContain(SUGERENCIA_ULTIMO_ERROR);
  });

  it('sin programadas lo dice y ofrece crear una', async () => {
    await iniciar([]);

    expect(texto()).toContain(MENSAJE_SIN_PROGRAMADAS);
    expect(
      Array.from(elemento().querySelectorAll('.centrado button')).map((b) =>
        normalizar(b.textContent),
      ),
    ).toEqual(['Nueva programada']);
  });

  it('un error al cargar ofrece Reintentar', async () => {
    fixture = TestBed.createComponent(ProgramadasPage);
    await estable();
    backend.expectOne(URL).error(new ProgressEvent('error'));
    backend.match(() => true);
    await estable();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toBe(MENSAJE_ERROR_CARGA);
    boton('Reintentar').click();
    await estable();
    responderCarga([programada()]);
    await estable();
    expect(filas()).toHaveLength(1);
  });

  it('una recarga descarta la respuesta de la anterior', async () => {
    fixture = TestBed.createComponent(ProgramadasPage);
    await estable();
    const viejas: TestRequest[] = backend.match(() => true);
    expect(viejas).toHaveLength(3);
    fixture.componentInstance['recargar']();
    await estable();

    expect(viejas.every((p) => p.cancelled)).toBe(true);
    responderCarga([programada({ beneficiario: 'Nueva' })]);
    await estable();
    expect(texto()).toContain('Nueva');
  });

  it('pausar reemplaza la fila y deshabilita sus acciones mientras espera', async () => {
    await iniciar();
    await menu(0, /Pausar/);

    const peticion = backend.expectOne(`${URL}/1/pausar`);
    expect(peticion.request.method).toBe('POST');
    expect((filas()[0].querySelector('button') as HTMLButtonElement).disabled).toBe(true);
    peticion.flush(programada({ activa: false }));
    await estable();

    expect(normalizar(filas()[0].textContent)).toContain('Pausada');
    expect((filas()[0].querySelector('button') as HTMLButtonElement).disabled).toBe(false);
    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_PAUSADA, 'Cerrar', expect.anything());
  });

  it('reanudar avisa que no se generan las ocurrencias del período pausado', async () => {
    await iniciar([programada({ activa: false })]);
    await menu(0, /Reanudar/);

    backend
      .expectOne(`${URL}/1/reanudar`)
      .flush(programada({ activa: true, proximaFecha: '2026-11-05' }));
    await estable();

    expect(normalizar(filas()[0].textContent)).toContain('Activa');
    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_REANUDADA, 'Cerrar', expect.anything());
  });

  it('borrar pide confirmación y, al confirmar, envía DELETE y quita la fila', async () => {
    await iniciar();
    resultadoDialogo = true;
    await menu(0, /Borrar/);

    expect(abrirDialogo).toHaveBeenCalledWith(DialogoConfirmarBorradoComponent, {
      data: { nombre: 'Inmobiliaria (Banco)' },
      width: '440px',
      maxWidth: '95vw',
    });
    const peticion = backend.expectOne(`${URL}/1`);
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await estable();

    expect(filas()).toHaveLength(0);
    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_BORRADA, 'Cerrar', expect.anything());
  });

  it('cancelar el borrado no envía nada', async () => {
    await iniciar();
    resultadoDialogo = false;
    await menu(0, /Borrar/);

    expect(backend.match(`${URL}/1`)).toHaveLength(0);
    expect(filas()).toHaveLength(1);
  });

  it('un 404 en una acción avisa y recarga la lista', async () => {
    await iniciar();
    await menu(0, /Pausar/);
    backend
      .expectOne(`${URL}/1/pausar`)
      .flush({ codigo: 'RECURSO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
    await estable();

    expect(abrirAviso).toHaveBeenCalledWith(
      MENSAJE_REFERENCIA_INEXISTENTE,
      'Cerrar',
      expect.anything(),
    );
    responderCarga([]);
    await estable();
    expect(texto()).toContain(MENSAJE_SIN_PROGRAMADAS);
  });

  it('otro error en una acción muestra el aviso genérico', async () => {
    await iniciar();
    await menu(0, /Pausar/);
    backend.expectOne(`${URL}/1/pausar`).error(new ProgressEvent('error'));
    await estable();

    expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', expect.anything());
  });

  it('Generar ahora se deshabilita mientras espera, anuncia el resultado y recarga', async () => {
    await iniciar();
    boton('Generar ahora').click();
    await estable();

    expect(boton('Generar ahora').disabled).toBe(true);
    backend.expectOne(`${URL}/generar`).flush({ generadas: 2, plantillasConError: 0 });
    await estable();

    expect(elemento().querySelector('.resultado[role="status"]')?.textContent?.trim()).toBe(
      'Se generaron 2 transacciones.',
    );
    responderCarga([programada()]);
    await estable();
    expect(boton('Generar ahora').disabled).toBe(false);
  });

  it('Generar ahora sin pendientes lo dice', async () => {
    await iniciar();
    boton('Generar ahora').click();
    await estable();
    backend.expectOne(`${URL}/generar`).flush({ generadas: 0, plantillasConError: 1 });
    await estable();
    responderCarga([programada()]);
    await estable();

    expect(texto()).toContain('No había ocurrencias pendientes. 1 programada no se pudo generar.');
  });

  it('crear abre el diálogo con las cuentas y categorías y recarga al guardar', async () => {
    await iniciar();
    resultadoDialogo = 'guardada';
    boton('Nueva programada').click();
    await estable();

    expect(abrirDialogo).toHaveBeenCalledWith(DialogoProgramadaComponent, {
      data: { cuentas: CUENTAS, grupos: GRUPOS, original: null },
      width: '600px',
      maxWidth: '95vw',
    });
    responderCarga([programada()]);
    await estable();
  });

  it('editar abre el diálogo con la programada', async () => {
    await iniciar();
    await menu(0, /Editar/);

    expect(abrirDialogo).toHaveBeenCalledWith(
      DialogoProgramadaComponent,
      expect.objectContaining({
        data: expect.objectContaining({ original: programada() }),
      }),
    );
  });
});
