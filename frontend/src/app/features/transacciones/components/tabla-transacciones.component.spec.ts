import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { MOTIVO_RECONCILIADA, MOTIVO_TRANSFERENCIA } from '../services/acciones-transaccion';
import { AccionFila, TablaTransaccionesComponent } from './tabla-transacciones.component';

function transaccion(id: number, cambios: Partial<TransaccionResponse> = {}): TransaccionResponse {
  return {
    id,
    cuentaId: 5,
    fecha: '2026-10-06',
    monto: -25000,
    categoriaId: 7,
    beneficiario: `Beneficiario ${id}`,
    memo: null,
    estado: 'NO_CONCILIADA',
    aprobada: true,
    subtransacciones: [],
    transaccionParId: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

describe('TablaTransaccionesComponent', () => {
  let fixture: ComponentFixture<TablaTransaccionesComponent>;
  let cargador: HarnessLoader;
  let acciones: AccionFila[];

  const filas = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tr.fila')) as HTMLElement[];
  const celda = (fila: number, clase: string) =>
    normalizar(filas()[fila].querySelector(`td.${clase}`)?.textContent);

  async function con(lista: TransaccionResponse[], seleccion: number[] = []): Promise<void> {
    fixture.componentRef.setInput('transacciones', lista);
    fixture.componentRef.setInput('seleccion', new Set(seleccion));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function itemsDelMenu(fila: number) {
    const menu = (await cargador.getAllHarnesses(MatMenuHarness))[fila];
    await menu.open();
    return menu.getItems();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(TablaTransaccionesComponent);
    fixture.componentRef.setInput('moneda', 'USD');
    fixture.componentRef.setInput(
      'nombresCuenta',
      new Map([
        [5, 'Banco'],
        [6, 'Efectivo'],
      ]),
    );
    fixture.componentRef.setInput(
      'nombresCategoria',
      new Map([
        [7, 'Comida'],
        [8, 'Ropa'],
      ]),
    );
    acciones = [];
    fixture.componentInstance.accion.subscribe((a) => acciones.push(a));
    cargador = TestbedHarnessEnvironment.loader(fixture);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('pone las salidas en Salida y las entradas en Entrada', async () => {
    await con([
      transaccion(1, { monto: -25000 }),
      transaccion(2, { monto: 100000, categoriaId: null }),
    ]);

    expect(celda(0, 'salida')).toContain('$25.00');
    expect(celda(0, 'entrada')).toBe('Entrada');
    expect(celda(1, 'entrada')).toContain('$100.00');
    expect(celda(1, 'salida')).toBe('Salida');
  });

  it('muestra cuenta, categoría, "Dividida" con las partes y vacío sin categoría', async () => {
    await con([
      transaccion(1),
      transaccion(2, {
        categoriaId: null,
        subtransacciones: [
          { id: 1, categoriaId: 7, monto: -10000, memo: null },
          { id: 2, categoriaId: 8, monto: -15000, memo: null },
        ],
      }),
      transaccion(3, { categoriaId: null, monto: 5000 }),
    ]);

    expect(celda(0, 'cuenta')).toContain('Banco');
    expect(celda(0, 'categoria')).toContain('Comida');
    expect(celda(1, 'categoria')).toContain('Dividida: Comida, Ropa');
    expect(celda(2, 'categoria')).toBe('Categoría');
  });

  it('el estado tiene ícono y texto accesible, y destaca las filas sin aprobar', async () => {
    await con([
      transaccion(1, { estado: 'RECONCILIADA' }),
      transaccion(2, { estado: 'CONCILIADA', aprobada: false }),
    ]);

    const estado0 = filas()[0].querySelector('.icono-estado') as HTMLElement;
    expect(estado0.textContent).toBe('lock');
    expect(estado0.getAttribute('aria-label')).toBe('Reconciliada');
    expect(filas()[1].querySelector('.icono-estado')?.getAttribute('aria-label')).toBe(
      'Conciliada',
    );
    expect(filas()[0].classList.contains('fila-sin-aprobar')).toBe(false);
    expect(filas()[1].classList.contains('fila-sin-aprobar')).toBe(true);
    expect(filas()[1].querySelector('.sin-aprobar')?.getAttribute('aria-label')).toBe(
      'Sin aprobar',
    );
  });

  it('las patas de transferencia llevan la insignia "Transferencia"', async () => {
    await con([transaccion(1), transaccion(2, { transaccionParId: 3 })]);

    expect(filas()[0].querySelector('.insignia-transferencia')).toBeNull();
    expect(celda(1, 'beneficiario')).toContain('Transferencia');
  });

  it('el menú de una normal sin aprobar ofrece todas las acciones y emite la elegida', async () => {
    await con([transaccion(1, { aprobada: false })]);

    const items = await itemsDelMenu(0);
    const textos = await Promise.all(items.map((i) => i.getText()));
    expect(textos.map((t) => t.replace(/^[a-z_]+/, ''))).toEqual([
      'Editar',
      'Duplicar',
      'Mover a otra cuenta',
      'Aprobar',
      'Marcar conciliada',
      'Borrar',
    ]);
    await items[3].click();
    expect(acciones).toEqual([
      { tipo: 'aprobar', transaccion: transaccion(1, { aprobada: false }) },
    ]);
  });

  it('una reconciliada deshabilita editar, mover, borrar y el estado con su motivo', async () => {
    await con([transaccion(1, { estado: 'RECONCILIADA' })]);

    const items = await itemsDelMenu(0);
    const estados = await Promise.all(
      items.map(async (i) => [await i.getText(), await i.isDisabled()] as const),
    );
    const deshabilitados = estados.filter(([, d]) => d).map(([t]) => t);
    expect(deshabilitados).toHaveLength(4);
    for (const texto of deshabilitados) {
      expect(texto).toContain(MOTIVO_RECONCILIADA);
    }
    expect(estados.find(([t]) => t.includes('Duplicar'))?.[1]).toBe(false);
  });

  it('una pata solo deja aprobar y cambiar el estado', async () => {
    await con([transaccion(1, { transaccionParId: 2, aprobada: false })]);

    const items = await itemsDelMenu(0);
    const habilitados: string[] = [];
    for (const item of items) {
      const texto = await item.getText();
      if (await item.isDisabled()) {
        expect(texto).toContain(MOTIVO_TRANSFERENCIA);
      } else {
        habilitados.push(texto.replace(/^[a-z_]+/, ''));
      }
    }
    expect(habilitados).toEqual(['Aprobar', 'Marcar conciliada']);
  });

  it('con la cuenta cerrada no ofrece Editar', async () => {
    fixture.componentRef.setInput('cuentasCerradas', new Set([5]));
    await con([transaccion(1)]);

    const textos = await Promise.all((await itemsDelMenu(0)).map((i) => i.getText()));
    expect(textos.some((t) => t.includes('Editar'))).toBe(false);
  });

  it('las casillas emiten la selección de una fila y de toda la página', async () => {
    const seleccion: number[] = [];
    const pagina: boolean[] = [];
    fixture.componentInstance.alternarSeleccion.subscribe((id) => seleccion.push(id));
    fixture.componentInstance.seleccionarPagina.subscribe((v) => pagina.push(v));
    await con([transaccion(1), transaccion(2)], [2]);

    const casillas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('mat-checkbox input'),
    ) as HTMLInputElement[];
    expect(casillas[0].indeterminate).toBe(true);
    casillas[1].click();
    casillas[0].click();

    expect(seleccion).toEqual([1]);
    expect(pagina).toEqual([true]);
  });
});
