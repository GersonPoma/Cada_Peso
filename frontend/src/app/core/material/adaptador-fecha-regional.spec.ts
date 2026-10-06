import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE } from '@angular/material/core';
import { describe, expect, it } from 'vitest';
import {
  AdaptadorFechaRegional,
  FORMATO_ENTRADA_FECHA,
  FORMATOS_FECHA_REGIONAL,
} from './adaptador-fecha-regional';

function adaptadorPara(region: string): DateAdapter<Date> {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: DateAdapter, useClass: AdaptadorFechaRegional },
      { provide: MAT_DATE_FORMATS, useValue: FORMATOS_FECHA_REGIONAL },
      { provide: MAT_DATE_LOCALE, useValue: region },
    ],
  });
  return TestBed.inject(DateAdapter) as DateAdapter<Date>;
}

function partes(fecha: Date | null): [number, number, number] | null {
  return fecha ? [fecha.getFullYear(), fecha.getMonth() + 1, fecha.getDate()] : null;
}

describe('AdaptadorFechaRegional', () => {
  describe('parse', () => {
    it('en es-BO interpreta 05/03/2003 como 5 de marzo de 2003', () => {
      expect(partes(adaptadorPara('es-BO').parse('05/03/2003', null))).toEqual([2003, 3, 5]);
    });

    it('en en-US interpreta 05/03/2003 como 3 de mayo de 2003', () => {
      expect(partes(adaptadorPara('en-US').parse('05/03/2003', null))).toEqual([2003, 5, 3]);
    });

    it('en es-BO acepta guion y punto como separadores', () => {
      const adaptador = adaptadorPara('es-BO');

      expect(partes(adaptador.parse('05-03-2003', null))).toEqual([2003, 3, 5]);
      expect(partes(adaptador.parse('05.03.2003', null))).toEqual([2003, 3, 5]);
    });

    it('en es-BO trata 31/02/2003 como inválida, nunca como 3 de marzo', () => {
      const adaptador = adaptadorPara('es-BO');
      const fecha = adaptador.parse('31/02/2003', null);

      expect(fecha).not.toBeNull();
      expect(adaptador.isValid(fecha as Date)).toBe(false);
    });

    it.each(['05/03/03', '05/2003', 'hoy', '5/3/2003x', '005/03/2003'])(
      'en es-BO trata "%s" como inválida',
      (texto) => {
        const adaptador = adaptadorPara('es-BO');
        const fecha = adaptador.parse(texto, null);

        expect(fecha).not.toBeNull();
        expect(adaptador.isValid(fecha as Date)).toBe(false);
      },
    );

    it('devuelve null para un campo vacío o null', () => {
      const adaptador = adaptadorPara('es-BO');

      expect(adaptador.parse('', null)).toBeNull();
      expect(adaptador.parse('   ', null)).toBeNull();
      expect(adaptador.parse(null, null)).toBeNull();
    });

    it('acepta día y mes de un dígito', () => {
      expect(partes(adaptadorPara('es-BO').parse('5/3/2003', null))).toEqual([2003, 3, 5]);
    });
  });

  describe('format', () => {
    const cincoDeMarzo = new Date(2003, 2, 5);

    it.each([
      ['es-BO', '05/03/2003'],
      ['es-CL', '05-03-2003'],
      ['en-US', '03/05/2003'],
      ['ja-JP', '2003/03/05'],
      ['hu-HU', '2003.03.05'],
    ])('en %s muestra el 5 de marzo de 2003 como %s', (region, esperado) => {
      expect(adaptadorPara(region).format(cincoDeMarzo, FORMATO_ENTRADA_FECHA)).toBe(esperado);
    });

    it.each(['es-BO', 'es-CL', 'en-US', 'ja-JP', 'hu-HU'])(
      'en %s lo mostrado se puede volver a escribir y da la misma fecha',
      (region) => {
        const adaptador = adaptadorPara(region);
        const texto = adaptador.format(cincoDeMarzo, FORMATO_ENTRADA_FECHA);

        expect(partes(adaptador.parse(texto, null))).toEqual([2003, 3, 5]);
      },
    );

    it('delega en el formato nativo para las etiquetas del calendario', () => {
      const adaptador = adaptadorPara('es-BO');
      const etiqueta = adaptador.format(
        cincoDeMarzo,
        FORMATOS_FECHA_REGIONAL.display.monthYearLabel,
      );

      expect(etiqueta.toLowerCase()).toContain('mar');
    });
  });
});
