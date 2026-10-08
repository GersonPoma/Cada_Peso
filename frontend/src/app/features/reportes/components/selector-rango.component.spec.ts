import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatSelectHarness } from '@angular/material/select/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mesActual } from '../../../shared/fecha/mes';
import { regionUsuario } from '../../../shared/formato/region-usuario';
import { RangoMeses } from '../models/rango-meses.model';
import { MENSAJE_RANGO_EXCEDIDO, MENSAJE_RANGO_INVERTIDO } from '../services/mensajes-reporte';
import { SelectorRangoComponent } from './selector-rango.component';

const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

/** Nombre del mes como lo muestra el selector en la región de la suite. */
const nombreMes = (numero: number) =>
  new Intl.DateTimeFormat(regionUsuario(), { month: 'long' }).format(new Date(2000, numero - 1, 1));

describe('SelectorRangoComponent', { timeout: 30000 }, () => {
  let fixture: ComponentFixture<SelectorRangoComponent>;
  let cargador: HarnessLoader;
  let emitidos: RangoMeses[];

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => (elemento().textContent ?? '').replace(/\s+/g, ' ');
  const boton = (t: string) =>
    Array.from(elemento().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === t,
    ) as HTMLButtonElement;
  const ETIQUETAS = ['Mes desde', 'Año desde', 'Mes hasta', 'Año hasta'];
  /** Los selectores están en el orden de `ETIQUETAS`. */
  const selector = async (etiqueta: string) =>
    (await cargador.getAllHarnesses(MatSelectHarness))[ETIQUETAS.indexOf(etiqueta)];

  async function crear(rango: RangoMeses, mesLocal = '2026-10'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    fixture = TestBed.createComponent(SelectorRangoComponent);
    fixture.componentRef.setInput('rango', rango);
    fixture.componentRef.setInput('mesLocal', mesLocal);
    emitidos = [];
    fixture.componentInstance.rangoCambiado.subscribe((r) => emitidos.push(r));
    cargador = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => {
    vi.useRealTimers();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  describe('con un rango', () => {
    beforeEach(() => crear({ desde: '2026-03', hasta: '2026-06' }));

    it('tiene las cuatro etiquetas y muestra el rango en texto', () => {
      for (const etiqueta of ETIQUETAS) {
        expect(texto()).toContain(etiqueta);
      }
      // El texto sigue la región del navegador de la suite.
      expect(elemento().querySelector('.texto-rango')?.textContent?.trim()).toMatch(/2026.*2026/);
    });

    it('ofrece los años de 2000 a 2100', async () => {
      const anio = await selector('Año desde');
      await anio.open();
      const opciones = await anio.getOptions();
      expect(opciones).toHaveLength(101);
      expect(await opciones[0].getText()).toBe('2000');
      expect(await opciones[100].getText()).toBe('2100');
    });

    it('emite el rango al cambiar un mes válido', async () => {
      await (await selector('Mes desde')).clickOptions({ text: nombreMes(1) });

      expect(emitidos).toEqual([{ desde: '2026-01', hasta: '2026-06' }]);
    });

    it('Desde posterior a Hasta se explica y no se emite', async () => {
      await (await selector('Mes desde')).clickOptions({ text: nombreMes(7) });
      fixture.detectChanges();

      expect(emitidos).toEqual([]);
      const alerta = elemento().querySelector('[role="alert"]');
      expect(alerta?.textContent?.trim()).toBe(MENSAJE_RANGO_INVERTIDO);
    });

    it('más de 60 meses se explica y no se emite', async () => {
      await (await selector('Año desde')).clickOptions({ text: '2021' });
      await (await selector('Mes desde')).clickOptions({ text: nombreMes(1) });
      await (await selector('Año hasta')).clickOptions({ text: '2026' });
      await (await selector('Mes hasta')).clickOptions({ text: nombreMes(1) });
      fixture.detectChanges();

      expect(elemento().querySelector('[role="alert"]')?.textContent?.trim()).toBe(
        MENSAJE_RANGO_EXCEDIDO,
      );
      expect(emitidos.some((r) => r.desde === '2021-01' && r.hasta === '2026-01')).toBe(false);
    });

    it.each([
      ['Este mes', '2026-10', '2026-10'],
      ['Últimos 3 meses', '2026-08', '2026-10'],
      ['Últimos 6 meses', '2026-05', '2026-10'],
      ['Últimos 12 meses', '2025-11', '2026-10'],
      ['Este año', '2026-01', '2026-10'],
    ])('el atajo %s emite %s a %s', (atajo, desde, hasta) => {
      boton(atajo).click();

      expect(emitidos).toEqual([{ desde, hasta }]);
    });
  });

  it.each([
    ['America/La_Paz', [2026, 9, 31, 21]],
    ['Asia/Tokyo', [2026, 9, 31, 8]],
  ] as const)('Este mes usa el mes local en %s', async (zona, fecha) => {
    process.env['TZ'] = zona;
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(fecha[0], fecha[1], fecha[2], fecha[3]));
    TestBed.configureTestingModule({
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    fixture = TestBed.createComponent(SelectorRangoComponent);
    fixture.componentRef.setInput('rango', { desde: '2026-01', hasta: '2026-02' });
    const recibidos: RangoMeses[] = [];
    fixture.componentInstance.rangoCambiado.subscribe((r) => recibidos.push(r));
    fixture.detectChanges();

    expect(mesActual()).toBe('2026-10');
    boton('Este mes').click();
    expect(recibidos).toEqual([{ desde: '2026-10', hasta: '2026-10' }]);
  });
});
