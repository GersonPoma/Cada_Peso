import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { GrupoMesResponse } from '../models/grupo-mes-response.model';
import { MetaMesResponse } from '../models/metas-mes-response.model';
import { GrupoMesComponent } from './grupo-mes.component';

function categoria(
  categoriaId: number,
  nombre: string,
  cifras: Partial<CategoriaMesResponse> = {},
): CategoriaMesResponse {
  return {
    categoriaId,
    nombre,
    oculta: false,
    asignado: 0,
    actividad: 0,
    disponible: 0,
    sobregastada: false,
    esPagoTarjeta: false,
    cuentaId: null,
    ...cifras,
  };
}

const FACTURAS: GrupoMesResponse = {
  id: 1,
  nombre: 'Facturas',
  orden: 0,
  oculto: false,
  categorias: [
    categoria(7, 'Luz', { asignado: 100000, actividad: -40000, disponible: 60000 }),
    categoria(8, 'Agua', {
      asignado: 50000,
      actividad: -80000,
      disponible: -30000,
      sobregastada: true,
    }),
    categoria(9, 'Gas', { oculta: true }),
  ],
};

function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();
}

/** El texto de un ítem del menú sin el nombre de su ícono (`swap_horizMover dinero...`). */
function sinIcono(texto: string): string {
  return texto.replace(/^[a-z_]+(?=[A-Z])/, '').trim();
}

describe('GrupoMesComponent', () => {
  let fixture: ComponentFixture<GrupoMesComponent>;
  let cargador: HarnessLoader;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const fila = (nombre: string) =>
    Array.from(elemento().querySelectorAll('.categoria')).find((f) =>
      f.querySelector('.nombre-categoria')?.textContent?.includes(nombre),
    ) as HTMLElement;
  const plegar = () => elemento().querySelector('.plegar') as HTMLButtonElement;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    vi.stubGlobal('navigator', { language: 'en-US', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(GrupoMesComponent);
    fixture.componentRef.setInput('grupo', FACTURAS);
    fixture.componentRef.setInput('moneda', 'USD');
    cargador = TestbedHarnessEnvironment.loader(fixture);
    await estable();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('el encabezado muestra el nombre y la suma de sus categorías', () => {
    const encabezado = elemento().querySelector('.encabezado-grupo');

    expect(encabezado?.textContent).toContain('Facturas');
    expect(normalizar(encabezado?.querySelector('.total-asignado')?.textContent)).toContain(
      '$150.00',
    );
    expect(normalizar(encabezado?.querySelector('.total-actividad')?.textContent)).toContain(
      '-$120.00',
    );
    expect(normalizar(encabezado?.querySelector('.total-disponible')?.textContent)).toContain(
      '$30.00',
    );
  });

  it('plegar oculta las categorías y cambia aria-expanded y el ícono', async () => {
    expect(plegar().getAttribute('aria-expanded')).toBe('true');
    expect(plegar().textContent).toContain('expand_more');

    plegar().click();
    await estable();

    expect(plegar().getAttribute('aria-expanded')).toBe('false');
    expect(plegar().textContent).toContain('chevron_right');
    expect(elemento().querySelectorAll('.categoria')).toHaveLength(0);
  });

  it('el disponible positivo, cero y sobregastado tiene su clase, ícono y texto', () => {
    const luz = fila('Luz').querySelector('.disponible') as HTMLElement;
    const agua = fila('Agua').querySelector('.disponible') as HTMLElement;
    const gas = fila('Gas').querySelector('.disponible') as HTMLElement;

    expect(luz.classList.contains('positivo')).toBe(true);
    expect(gas.classList.contains('positivo')).toBe(false);
    expect(gas.classList.contains('sobregastado')).toBe(false);
    expect(agua.classList.contains('sobregastado')).toBe(true);
    expect(agua.querySelector('.advertencia')?.textContent).toBe('warning');
    expect(agua.querySelector('.texto-sobregasto')?.textContent).toBe('Sobregastado');
    expect(normalizar(agua.textContent)).toContain('-$30.00');
  });

  it('las categorías ocultas se ven atenuadas', () => {
    expect(fila('Gas').classList.contains('oculta')).toBe(true);
    expect(fila('Luz').classList.contains('oculta')).toBe(false);
  });

  it('reenvía el guardado de una celda como asignar', () => {
    const emitidos: unknown[] = [];
    fixture.componentInstance.asignar.subscribe((valor) => emitidos.push(valor));

    const celda = fixture.debugElement.queryAll((nodo) => nodo.name === 'app-celda-asignado')[0];
    celda.triggerEventHandler('guardar', 123000);

    expect(emitidos).toEqual([{ categoriaId: 7, asignado: 123000 }]);
  });

  it('el menú ofrece Mover dinero en todas y Cubrir sobregasto solo en las sobregastadas', async () => {
    const mover: CategoriaMesResponse[] = [];
    const cubrir: CategoriaMesResponse[] = [];
    fixture.componentInstance.moverDinero.subscribe((c) => mover.push(c));
    fixture.componentInstance.cubrirSobregasto.subscribe((c) => cubrir.push(c));

    const menuLuz = await cargador.getHarness(
      MatMenuHarness.with({ selector: '[aria-label="Acciones de Luz"]' }),
    );
    await menuLuz.open();
    const itemsLuz = await Promise.all((await menuLuz.getItems()).map((i) => i.getText()));
    expect(itemsLuz).toHaveLength(2);
    expect(itemsLuz[0]).toContain('Mover dinero...');
    expect(itemsLuz.some((t) => t.includes('Cubrir sobregasto'))).toBe(false);
    await menuLuz.clickItem({ text: /Mover dinero/ });

    const menuAgua = await cargador.getHarness(
      MatMenuHarness.with({ selector: '[aria-label="Acciones de Agua"]' }),
    );
    await menuAgua.open();
    await menuAgua.clickItem({ text: /Cubrir sobregasto/ });

    expect(mover.map((c) => c.nombre)).toEqual(['Luz']);
    expect(cubrir.map((c) => c.nombre)).toEqual(['Agua']);
  });

  describe('metas', () => {
    function meta(categoriaId: number, cambios: Partial<MetaMesResponse> = {}): MetaMesResponse {
      return {
        categoriaId,
        nombre: 'Luz',
        tipo: 'MONTO_MENSUAL',
        monto: 100000,
        necesidad: 100000,
        asignado: 60000,
        disponible: 60000,
        faltante: 40000,
        estado: 'FALTA',
        ...cambios,
      };
    }

    async function conMetas(...metas: MetaMesResponse[]): Promise<void> {
      fixture.componentRef.setInput('metas', new Map(metas.map((m) => [m.categoriaId, m])));
      await estable();
    }

    async function textosDelMenu(nombre: string): Promise<string[]> {
      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: `[aria-label="Acciones de ${nombre}"]` }),
      );
      await menu.open();
      const textos = await Promise.all((await menu.getItems()).map((i) => i.getText()));
      await menu.close();
      return textos.map(sinIcono);
    }

    it('el indicador va debajo del nombre solo en las categorías con meta', async () => {
      await conMetas(meta(7));

      const indicadorLuz = fila('Luz').querySelector('.col-nombre app-indicador-meta');
      expect(indicadorLuz).not.toBeNull();
      expect(normalizar(indicadorLuz?.textContent)).toContain('Falta $40.00');
      expect(fila('Agua').querySelector('app-indicador-meta')).toBeNull();
    });

    it('en un mes pasado el indicador usa el tono neutro', async () => {
      fixture.componentRef.setInput('mesPasado', true);
      await conMetas(meta(7));

      expect(normalizar(fila('Luz').querySelector('.estado')?.textContent)).toContain('Faltaron');
    });

    it('sin meta ofrece Agregar meta y nada para posponer', async () => {
      expect(await textosDelMenu('Luz')).toEqual(['Mover dinero...', 'Agregar meta']);
    });

    it('con meta en FALTA ofrece Editar meta y Posponer este mes', async () => {
      await conMetas(meta(7));

      expect(await textosDelMenu('Luz')).toEqual([
        'Mover dinero...',
        'Editar meta',
        'Posponer este mes',
      ]);
    });

    it('con meta pospuesta ofrece Reanudar este mes', async () => {
      await conMetas(meta(7, { estado: 'POSPUESTA' }));

      expect(await textosDelMenu('Luz')).toEqual([
        'Mover dinero...',
        'Editar meta',
        'Reanudar este mes',
      ]);
    });

    it('con meta sobregastada ofrece posponer y reanudar', async () => {
      await conMetas(meta(8, { nombre: 'Agua', estado: 'SOBREGASTADA' }));

      expect(await textosDelMenu('Agua')).toEqual([
        'Mover dinero...',
        'Cubrir sobregasto',
        'Editar meta',
        'Posponer este mes',
        'Reanudar este mes',
      ]);
    });

    it('emite editar, posponer y reanudar con la categoría', async () => {
      const editar: string[] = [];
      const posponer: string[] = [];
      const reanudar: string[] = [];
      fixture.componentInstance.editarMeta.subscribe((c) => editar.push(c.nombre));
      fixture.componentInstance.posponerMeta.subscribe((c) => posponer.push(c.nombre));
      fixture.componentInstance.reanudarMeta.subscribe((c) => reanudar.push(c.nombre));
      await conMetas(meta(8, { nombre: 'Agua', estado: 'SOBREGASTADA' }));

      for (const [nombre, item] of [
        ['Luz', /Agregar meta/],
        ['Agua', /Editar meta/],
        ['Agua', /Posponer este mes/],
        ['Agua', /Reanudar este mes/],
      ] as const) {
        const menu = await cargador.getHarness(
          MatMenuHarness.with({ selector: `[aria-label="Acciones de ${nombre}"]` }),
        );
        await menu.open();
        await menu.clickItem({ text: item });
      }

      expect(editar).toEqual(['Luz', 'Agua']);
      expect(posponer).toEqual(['Agua']);
      expect(reanudar).toEqual(['Agua']);
    });

    it('con una acción en curso las acciones de meta de esa fila se deshabilitan', async () => {
      await conMetas(meta(7));
      fixture.componentRef.setInput('metasEnCurso', new Set([7]));
      await estable();
      const menu = await cargador.getHarness(
        MatMenuHarness.with({ selector: '[aria-label="Acciones de Luz"]' }),
      );
      await menu.open();
      const items = await menu.getItems();
      const estados = await Promise.all(
        items.map(async (i) => [await i.getText(), await i.isDisabled()] as const),
      );

      expect(estados.map(([texto, deshabilitado]) => [sinIcono(texto), deshabilitado])).toEqual([
        ['Mover dinero...', false],
        ['Editar meta', true],
        ['Posponer este mes', true],
      ]);
    });
  });
});
