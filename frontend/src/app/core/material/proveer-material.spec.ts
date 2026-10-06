import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE } from '@angular/material/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { MatIconRegistry } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdaptadorFechaRegional } from './adaptador-fecha-regional';
import { CalendarioIntl } from './calendario-intl';
import { PaginadorIntl } from './paginador-intl';
import { proveerMaterial } from './proveer-material';

function configurarConRegion(region: string): void {
  vi.stubGlobal('navigator', { language: region });
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [proveerMaterial()] });
}

describe('proveerMaterial', () => {
  const primeroDeOctubre = new Date(2026, 9, 1);

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('con es-CL usa esa región y el adaptador regional para las fechas', () => {
    configurarConRegion('es-CL');
    const adaptador = TestBed.inject(DateAdapter) as DateAdapter<Date>;
    const formatos = TestBed.inject(MAT_DATE_FORMATS);

    expect(TestBed.inject(MAT_DATE_LOCALE)).toBe('es-CL');
    expect(adaptador).toBeInstanceOf(AdaptadorFechaRegional);
    expect(adaptador.format(primeroDeOctubre, formatos.display.dateInput)).toBe('01-10-2026');
    expect(adaptador.getMonthNames('long')[9]).toBe('octubre');
  });

  it('con en-US muestra el mes antes que el día', () => {
    configurarConRegion('en-US');
    const adaptador = TestBed.inject(DateAdapter) as DateAdapter<Date>;
    const formatos = TestBed.inject(MAT_DATE_FORMATS);

    expect(adaptador.format(primeroDeOctubre, formatos.display.dateInput)).toBe('10/01/2026');
  });

  it('registra los textos en español del paginador y del calendario', () => {
    configurarConRegion('es-CL');

    expect(TestBed.inject(MatPaginatorIntl)).toBeInstanceOf(PaginadorIntl);
    expect(TestBed.inject(MatDatepickerIntl)).toBeInstanceOf(CalendarioIntl);
  });

  it('usa Material Symbols Outlined como fuente por defecto de mat-icon', () => {
    configurarConRegion('es-CL');

    expect(TestBed.inject(MatIconRegistry).getDefaultFontSetClass()).toContain(
      'material-symbols-outlined',
    );
  });
});
