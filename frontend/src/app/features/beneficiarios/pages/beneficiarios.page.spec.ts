import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { DialogoBeneficiarioComponent } from '../components/dialogo-beneficiario.component';
import { BeneficiarioResponse } from '../models/beneficiario-response.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { BeneficiariosPage } from './beneficiarios.page';

const URL = '/api/v1/presupuestos/3/beneficiarios';
const URL_CATEGORIAS = '/api/v1/presupuestos/3/categorias';

const GRUPOS: GrupoCategoriasLectura[] = [
  {
    id: 1,
    nombre: 'Gustos',
    oculto: false,
    categorias: [{ id: 7, nombre: 'Ocio', oculta: false, esPagoTarjeta: false }],
  },
];

const BANCO_UNION: BeneficiarioResponse = {
  id: 1,
  nombre: 'Banco Unión',
  categoriaPredeterminadaId: null,
};
const NESTLE: BeneficiarioResponse = { id: 2, nombre: 'Nestlé', categoriaPredeterminadaId: 99 };
const NETFLIX: BeneficiarioResponse = { id: 4, nombre: 'Netflix', categoriaPredeterminadaId: 7 };

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

describe('BeneficiariosPage', () => {
  let fixture: ComponentFixture<BeneficiariosPage>;
  let backend: HttpTestingController;
  let abrirDialogo: ReturnType<typeof vi.spyOn>;
  let resultadoDialogo: unknown;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => normalizar(elemento().textContent);
  const filas = () =>
    Array.from(elemento().querySelectorAll('tr.fila')).map((f) => [
      normalizar(f.querySelector('.nombre')?.textContent),
      normalizar(f.querySelector('.categoria')?.textContent),
    ]);
  const botonCon = (contenido: string) =>
    Array.from(elemento().querySelectorAll('button')).find((b) =>
      b.textContent?.includes(contenido),
    ) as HTMLButtonElement | undefined;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function responder(lista: BeneficiarioResponse[]): Promise<void> {
    backend.expectOne(URL).flush(lista);
    backend
      .expectOne((p) => p.url === URL_CATEGORIAS && p.params.get('incluirOcultas') === 'true')
      .flush(GRUPOS);
    await estable();
  }

  async function buscar(textoBuscado: string): Promise<void> {
    const entrada = elemento().querySelector('input[type="search"]') as HTMLInputElement;
    entrada.value = textoBuscado;
    entrada.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(250);
    await estable();
  }

  beforeEach(async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    resultadoDialogo = undefined;
    abrirDialogo = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockImplementation(
        () => ({ afterClosed: () => of(resultadoDialogo) }) as MatDialogRef<unknown, unknown>,
      );
    fixture = TestBed.createComponent(BeneficiariosPage);
    await estable();
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('muestra un indicador mientras carga', () => {
    expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
    backend.expectOne(URL).flush([]);
    backend.expectOne((p) => p.url === URL_CATEGORIAS).flush([]);
  });

  it('lista los beneficiarios con su categoría o —, cada uno con Editar', async () => {
    await responder([BANCO_UNION, NETFLIX]);

    expect(filas()).toEqual([
      ['Banco Unión', '—'],
      ['Netflix', 'Ocio'],
    ]);
    expect(elemento().querySelector('[aria-label="Editar Banco Unión"]')).not.toBeNull();
    expect(elemento().querySelector('[aria-label="Editar Netflix"]')).not.toBeNull();
    expect(texto()).not.toContain('Borrar');
  });

  it('una categoría que ya no existe se muestra como —', async () => {
    await responder([NESTLE]);

    expect(filas()).toEqual([['Nestlé', '—']]);
  });

  it('filtra por prefijo sin distinguir mayúsculas y sin pedir de nuevo', async () => {
    await responder([BANCO_UNION, NESTLE, NETFLIX]);

    await buscar('NE');

    expect(filas().map(([nombre]) => nombre)).toEqual(['Nestlé', 'Netflix']);
    backend.expectNone(URL);
  });

  it('espera 250 ms antes de filtrar', async () => {
    await responder([BANCO_UNION, NETFLIX]);
    const entrada = elemento().querySelector('input[type="search"]') as HTMLInputElement;
    entrada.value = 'net';
    entrada.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(100);
    await estable();
    expect(filas()).toHaveLength(2);

    await vi.advanceTimersByTimeAsync(150);
    await estable();
    expect(filas().map(([nombre]) => nombre)).toEqual(['Netflix']);
  });

  it('avisa si ningún nombre coincide', async () => {
    await responder([NETFLIX]);

    await buscar('zzz');

    expect(texto()).toContain('Ningún beneficiario coincide con la búsqueda.');
  });

  it('sin beneficiarios muestra el mensaje vacío y Agregar beneficiario', async () => {
    await responder([]);

    expect(texto()).toContain(
      'Aún no tienes beneficiarios; se crean solos al registrar transacciones',
    );
    expect(botonCon('Agregar beneficiario')).toBeDefined();
  });

  it('si la carga falla muestra el error y Reintentar vuelve a pedir', async () => {
    const categorias = backend.expectOne((p) => p.url === URL_CATEGORIAS);
    backend.expectOne(URL).flush(null, { status: 500, statusText: 'Error' });
    // forkJoin cancela la otra petición al fallar una.
    expect(categorias.cancelled).toBe(true);
    await estable();
    expect(texto()).toContain('No pudimos cargar tus beneficiarios.');

    botonCon('Reintentar')?.click();
    await estable();
    await responder([NETFLIX]);

    expect(filas()).toEqual([['Netflix', 'Ocio']]);
  });

  it('Agregar abre el diálogo para crear y recarga tras guardar', async () => {
    await responder([NETFLIX]);
    resultadoDialogo = { tipo: 'guardado' };

    botonCon('Agregar beneficiario')?.click();
    await estable();

    expect(abrirDialogo).toHaveBeenCalledWith(
      DialogoBeneficiarioComponent,
      expect.objectContaining({ data: { beneficiario: null, grupos: GRUPOS } }),
    );
    await responder([NETFLIX, BANCO_UNION]);
  });

  it('Editar abre el diálogo con el beneficiario y recarga si pide recargar', async () => {
    await responder([NETFLIX]);
    resultadoDialogo = { tipo: 'recargar' };

    (elemento().querySelector('[aria-label="Editar Netflix"]') as HTMLButtonElement).click();
    await estable();

    expect(abrirDialogo).toHaveBeenCalledWith(
      DialogoBeneficiarioComponent,
      expect.objectContaining({ data: { beneficiario: NETFLIX, grupos: GRUPOS } }),
    );
    await responder([]);
  });

  it('cancelar el diálogo no recarga', async () => {
    await responder([NETFLIX]);

    (elemento().querySelector('[aria-label="Editar Netflix"]') as HTMLButtonElement).click();
    await estable();

    backend.expectNone(URL);
  });
});
