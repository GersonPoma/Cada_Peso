import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { TransaccionResponse } from '../models/transaccion-response.model';
import {
  MOTIVO_CUENTA_CERRADA,
  MOTIVO_RECONCILIADA,
  MOTIVO_TRANSFERENCIA,
  MOTIVO_TRANSFERENCIA_RECONCILIADA,
} from '../services/acciones-transaccion';
import { AccionFila, TablaTransaccionesComponent } from './tabla-transacciones.component';

function transaccion(id: number, cambios: Partial<TransaccionResponse> = {}): TransaccionResponse {
  return {
    id,
    cuentaId: 5,
    fecha: '2026-10-06',
    monto: -25000,
    categoriaId: 7,
    beneficiario: `Beneficiario ${id}`,
    beneficiarioId: null,
    memo: null,
    estado: 'NO_CONCILIADA',
    aprobada: true,
    subtransacciones: [],
    transaccionParId: null,
    programadaId: null,
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

  describe('beneficiario', () => {
    beforeEach(() => {
      fixture.componentRef.setInput(
        'nombresBeneficiario',
        new Map([
          [4, 'Netflix Premium'],
          [5, 'Tienda Sol'],
        ]),
      );
    });

    it('muestra el nombre actual del beneficiario vinculado aunque se haya renombrado', async () => {
      await con([transaccion(1, { beneficiario: 'Netflix', beneficiarioId: 4 })]);

      expect(celda(0, 'beneficiario')).toBe('Beneficiario Netflix Premium');
    });

    it('sin vínculo muestra el texto de la transacción', async () => {
      await con([transaccion(1, { beneficiario: 'Tienda', beneficiarioId: null })]);

      expect(celda(0, 'beneficiario')).toBe('Beneficiario Tienda');
    });

    it('con un id que no está en la lista muestra el texto de la transacción', async () => {
      await con([transaccion(1, { beneficiario: 'Viejo', beneficiarioId: 9 })]);

      expect(celda(0, 'beneficiario')).toBe('Beneficiario Viejo');
    });
  });

  it('las patas de transferencia llevan la insignia "Transferencia"', async () => {
    await con([transaccion(1), transaccion(2, { transaccionParId: 3 })]);

    expect(filas()[0].querySelector('.insignia-transferencia')).toBeNull();
    expect(filas()[1].querySelector('.insignia-transferencia')).not.toBeNull();
  });

  it('una pata dice a qué cuenta va o de cuál viene si su par está en la página', async () => {
    await con([
      transaccion(1, { cuentaId: 5, monto: -10000, transaccionParId: 2, beneficiario: null }),
      transaccion(2, { cuentaId: 6, monto: 10000, transaccionParId: 1, beneficiario: null }),
    ]);

    expect(celda(0, 'beneficiario')).toBe(
      'Beneficiario Transferencia a Efectivo swap_horizTransferencia',
    );
    expect(celda(1, 'beneficiario')).toBe(
      'Beneficiario Transferencia desde Banco swap_horizTransferencia',
    );
  });

  it('sin la pata par en la página solo dice Transferencia', async () => {
    await con([transaccion(1, { transaccionParId: 99, beneficiario: null })]);

    expect(celda(0, 'beneficiario')).toBe('Beneficiario Transferencia swap_horizTransferencia');
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

  describe('menú de una pata de transferencia', () => {
    async function estadosDelMenu(fila: number) {
      const items = await itemsDelMenu(fila);
      return Promise.all(
        items.map(
          async (i) => [(await i.getText()).replace(/^[a-z_]+/, ''), await i.isDisabled()] as const,
        ),
      );
    }

    it('ofrece editar y borrar la transferencia; no duplica ni mueve', async () => {
      await con([transaccion(1, { transaccionParId: 2, aprobada: false })]);

      const estados = await estadosDelMenu(0);
      expect(estados).toEqual([
        ['Editar transferencia', false],
        [`Duplicar${MOTIVO_TRANSFERENCIA}`, true],
        [`Mover a otra cuenta${MOTIVO_TRANSFERENCIA}`, true],
        ['Aprobar', false],
        ['Marcar conciliada', false],
        ['Borrar transferencia', false],
      ]);
    });

    it('emite editar y borrar la transferencia con la transacción de la fila', async () => {
      const pata = transaccion(1, { transaccionParId: 2 });
      await con([pata]);
      await (await itemsDelMenu(0))[0].click();
      const items = await itemsDelMenu(0);
      await items[items.length - 1].click();

      expect(acciones).toEqual([
        { tipo: 'editarTransferencia', transaccion: pata },
        { tipo: 'borrarTransferencia', transaccion: pata },
      ]);
    });

    it('reconciliada: editar y borrar la transferencia deshabilitados con el motivo', async () => {
      await con([transaccion(1, { transaccionParId: 2, estado: 'RECONCILIADA' })]);

      const estados = await estadosDelMenu(0);
      expect(estados[0]).toEqual([
        `Editar transferencia${MOTIVO_TRANSFERENCIA_RECONCILIADA}`,
        true,
      ]);
      expect(estados.at(-1)).toEqual([
        `Borrar transferencia${MOTIVO_TRANSFERENCIA_RECONCILIADA}`,
        true,
      ]);
    });

    it('con la pata par reconciliada en la página también se bloquea', async () => {
      await con([
        transaccion(1, { transaccionParId: 2 }),
        transaccion(2, { transaccionParId: 1, cuentaId: 6, estado: 'RECONCILIADA' }),
      ]);

      expect((await estadosDelMenu(0))[0]).toEqual([
        `Editar transferencia${MOTIVO_TRANSFERENCIA_RECONCILIADA}`,
        true,
      ]);
    });

    it('con la cuenta cerrada se bloquea con su motivo', async () => {
      fixture.componentRef.setInput('cuentasCerradas', new Set([5]));
      await con([transaccion(1, { transaccionParId: 2 })]);

      const estados = await estadosDelMenu(0);
      expect(estados[0]).toEqual([`Editar transferencia${MOTIVO_CUENTA_CERRADA}`, true]);
      expect(estados.at(-1)).toEqual([`Borrar transferencia${MOTIVO_CUENTA_CERRADA}`, true]);
    });
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
