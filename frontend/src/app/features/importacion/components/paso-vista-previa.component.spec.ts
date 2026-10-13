import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PaginadorIntl } from '../../../core/material/paginador-intl';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { FilaPrevia, VistaPrevia } from '../models/vista-previa-response.model';
import { PasoVistaPreviaComponent } from './paso-vista-previa.component';

function fila(numero: number, cambios: Partial<FilaPrevia> = {}): FilaPrevia {
  return {
    fila: numero,
    fecha: '2026-10-13',
    monto: -135000,
    beneficiario: `Beneficiario ${numero}`,
    memo: numero % 2 === 0 ? `Memo ${numero}` : null,
    estado: 'NUEVA',
    motivo: null,
    ...cambios,
  };
}

const INVALIDA: Partial<FilaPrevia> = {
  fecha: null,
  monto: null,
  beneficiario: null,
  memo: null,
  estado: 'INVALIDA',
  motivo: 'La fecha no es válida',
};

/** 6 nuevas, 3 duplicadas y 1 inválida. */
function diezFilas(): VistaPrevia {
  const filas = [
    ...Array.from({ length: 6 }, (_, i) => fila(i + 1)),
    ...Array.from({ length: 3 }, (_, i) => fila(i + 7, { estado: 'DUPLICADA' })),
    fila(10, INVALIDA),
  ];
  return {
    totales: { total: 10, nuevas: 6, duplicadas: 3, invalidas: 1, sinCategoria: 6 },
    filas,
  };
}

function muchasFilas(cantidad: number): VistaPrevia {
  return {
    totales: {
      total: cantidad,
      nuevas: cantidad,
      duplicadas: 0,
      invalidas: 0,
      sinCategoria: cantidad,
    },
    filas: Array.from({ length: cantidad }, (_, i) => fila(i + 1)),
  };
}

describe('PasoVistaPreviaComponent', () => {
  let fixture: ComponentFixture<PasoVistaPreviaComponent>;
  let cargador: HarnessLoader;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => (elemento().textContent ?? '').replace(/\s+/g, ' ');
  const filas = () => Array.from(elemento().querySelectorAll('tbody tr, tr[mat-row]'));
  const celdas = (f: Element) =>
    Array.from(f.querySelectorAll('td')).map((td) => td.textContent?.replace(/\s+/g, ' ').trim());

  async function mostrar(vistaPrevia: VistaPrevia, region = 'es-BO'): Promise<void> {
    vi.stubGlobal('navigator', { language: region, userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        { provide: MatPaginatorIntl, useClass: PaginadorIntl },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    fixture = TestBed.createComponent(PasoVistaPreviaComponent);
    cargador = TestbedHarnessEnvironment.loader(fixture);
    fixture.componentRef.setInput('vistaPrevia', vistaPrevia);
    fixture.componentRef.setInput('moneda', 'BOB');
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => vi.unstubAllGlobals());

  describe('totales y filtro', () => {
    beforeEach(() => mostrar(diezFilas()));

    it('muestra los totales como texto', () => {
      const totales = Array.from(elemento().querySelectorAll('.totales li')).map((li) =>
        li.textContent?.trim(),
      );

      expect(totales).toEqual(['10 filas en total', '6 nuevas', '3 duplicadas', '1 inválida']);
    });

    it('el título del paso es enfocable por código', () => {
      const titulo = elemento().querySelector('h2.titulo-paso') as HTMLElement;

      expect(titulo.getAttribute('tabindex')).toBe('-1');
      expect(titulo.textContent).toContain('Revisa la vista previa');
    });

    it('Todas muestra las diez filas', () => {
      expect(filas()).toHaveLength(10);
    });

    it('Inválidas muestra solo las inválidas con su motivo y el paginador las cuenta', async () => {
      const filtro = await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Inválidas' }));
      await filtro.check();

      expect(filas()).toHaveLength(1);
      expect(texto()).toContain('La fecha no es válida');
      const paginador = await cargador.getHarness(MatPaginatorHarness);
      expect(await paginador.getRangeLabel()).toBe('1 – 1 de 1');
    });

    it('Nuevas y Duplicadas filtran por su estado', async () => {
      await (await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Nuevas' }))).check();
      expect(filas()).toHaveLength(6);

      await (
        await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Duplicadas' }))
      ).check();
      expect(filas()).toHaveLength(3);

      await (await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Todas' }))).check();
      expect(filas()).toHaveLength(10);
    });

    it('la región de la tabla se puede enfocar y tiene nombre', () => {
      const region = elemento().querySelector('[role="region"]') as HTMLElement;

      expect(region.getAttribute('tabindex')).toBe('0');
      expect(region.getAttribute('aria-label')).toBe('Filas de la vista previa');
      expect(region.querySelector('table')).not.toBeNull();
    });

    it('Volver al mapeo emite', () => {
      let pulsado = 0;
      fixture.componentInstance.volver.subscribe(() => pulsado++);

      (elemento().querySelector('button.volver') as HTMLButtonElement).click();

      expect(pulsado).toBe(1);
    });
  });

  describe('sin filas para un estado', () => {
    it('un filtro sin filas lo dice', async () => {
      const sinDuplicadas: VistaPrevia = {
        totales: { total: 1, nuevas: 1, duplicadas: 0, invalidas: 0, sinCategoria: 1 },
        filas: [fila(1)],
      };
      await mostrar(sinDuplicadas);

      await (
        await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Duplicadas' }))
      ).check();

      expect(filas()).toHaveLength(0);
      expect(texto()).toContain('No hay filas con este estado.');
    });
  });

  describe('columnas y estados', () => {
    beforeEach(() => mostrar(diezFilas()));

    it('la columna del número se llama Fila de datos y empieza en 1', () => {
      const encabezados = Array.from(elemento().querySelectorAll('th')).map((th) =>
        th.textContent?.trim(),
      );

      expect(encabezados).toEqual([
        'Fila de datos',
        'Fecha',
        'Monto',
        'Beneficiario',
        'Memo',
        'Estado',
        'Motivo',
      ]);
      expect(celdas(filas()[0])[0]).toBe('1');
    });

    it('una fila inválida muestra guiones, su estado con ícono y su motivo', () => {
      const invalida = celdas(filas()[9]);

      expect(invalida[1]).toBe('—');
      expect(invalida[2]).toBe('—');
      expect(invalida[3]).toBe('—');
      expect(invalida[4]).toBe('—');
      expect(invalida[5]).toContain('Inválida');
      expect(invalida[6]).toBe('La fecha no es válida');
      expect(filas()[9].querySelector('.estado mat-icon')?.textContent?.trim()).toBe('error');
    });

    it('cada estado se ve con texto e ícono', () => {
      const estados = filas().map((f) => ({
        texto: f.querySelector('.estado')?.textContent?.replace(/\s+/g, ' ').trim(),
        icono: f.querySelector('.estado mat-icon')?.textContent?.trim(),
      }));

      expect(estados[0]).toEqual({ texto: 'add_circle Nueva', icono: 'add_circle' });
      expect(estados[6]).toEqual({ texto: 'content_copy Duplicada', icono: 'content_copy' });
      expect(estados[9]).toEqual({ texto: 'error Inválida', icono: 'error' });
    });
  });

  describe('formato regional', () => {
    it('en es-BO el monto es -Bs 135,00 y la fecha sale del pipe de la región', async () => {
      await mostrar(diezFilas(), 'es-BO');
      const primera = celdas(filas()[0]);

      expect(primera[2]).toBe('-Bs 135,00');
      expect(primera[1]).toBe(new FechaPipe().transform('2026-10-13'));
    });

    it('en en-US el monto cambia de formato', async () => {
      await mostrar(diezFilas(), 'en-US');

      expect(celdas(filas()[0])[2]).toBe('-BOB 135.00');
    });
  });

  describe('paginación en el cliente', () => {
    beforeEach(() => mostrar(muchasFilas(5000)));

    it('con 5000 filas muestra una página de 25 y el rango en español', async () => {
      const paginador = await cargador.getHarness(MatPaginatorHarness);

      expect(filas()).toHaveLength(25);
      expect(await paginador.getRangeLabel()).toBe('1 – 25 de 5000');
    });

    it('cambiar de página recorta otras filas sin pedir nada', async () => {
      const paginador = await cargador.getHarness(MatPaginatorHarness);

      await paginador.goToNextPage();

      expect(await paginador.getRangeLabel()).toBe('26 – 50 de 5000');
      expect(celdas(filas()[0])[0]).toBe('26');
      expect(filas()).toHaveLength(25);
    });

    it('cambiar el tamaño de página lo respeta', async () => {
      const paginador = await cargador.getHarness(MatPaginatorHarness);

      await paginador.setPageSize(100);

      expect(filas()).toHaveLength(100);
      expect(await paginador.getRangeLabel()).toBe('1 – 100 de 5000');
    });

    it('cambiar el filtro vuelve a la primera página', async () => {
      const paginador = await cargador.getHarness(MatPaginatorHarness);
      await paginador.goToNextPage();

      await (await cargador.getHarness(MatButtonToggleHarness.with({ text: 'Nuevas' }))).check();

      expect(await paginador.getRangeLabel()).toBe('1 – 25 de 5000');
      expect(celdas(filas()[0])[0]).toBe('1');
    });
  });
});
