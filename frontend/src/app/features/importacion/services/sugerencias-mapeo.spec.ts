import { describe, expect, it } from 'vitest';
import {
  cabeEnFormato,
  decimalDeLaRegion,
  formatoDeFechaRegional,
  sugerirDecimal,
  sugerirEncabezado,
  sugerirFormatoFecha,
  sugerirMapeo,
  sugerirSeparador,
} from './sugerencias-mapeo';

describe('regiones', () => {
  it('el orden de la fecha sale de la región', () => {
    expect(formatoDeFechaRegional('es-BO')).toBe('dd/MM/yyyy');
    expect(formatoDeFechaRegional('en-US')).toBe('MM/dd/yyyy');
    expect(formatoDeFechaRegional('ja-JP')).toBe('yyyy-MM-dd');
  });

  it('una región inválida cae en el orden de en-US', () => {
    expect(formatoDeFechaRegional('no-es-una-region-@@')).toBe('MM/dd/yyyy');
  });

  it('el decimal sale de la región', () => {
    expect(decimalDeLaRegion('es-BO')).toBe('COMA');
    expect(decimalDeLaRegion('en-US')).toBe('PUNTO');
  });
});

describe('sugerirSeparador', () => {
  it('coma', () => {
    expect(sugerirSeparador('fecha,desc,monto\n13/10/2026,Cafe,-12.5')).toBe('COMA');
  });

  it('punto y coma', () => {
    expect(sugerirSeparador('fecha;desc;monto\n13/10/2026;Cafe;-12,5')).toBe('PUNTO_Y_COMA');
  });

  it('tabulador', () => {
    expect(sugerirSeparador('fecha\tdesc\tmonto\n13/10/2026\tCafe\t-12.5')).toBe('TABULADOR');
  });

  it('con comas dentro de comillas gana el separador de campos', () => {
    const texto =
      'fecha;desc;monto\n13/10/2026;"Pizzería, Marco";-1,5\n14/10/2026;"Otra, cosa";-2,5';

    expect(sugerirSeparador(texto)).toBe('PUNTO_Y_COMA');
  });

  it('con una columna sola o sin pistas es la coma', () => {
    expect(sugerirSeparador('hola\nmundo')).toBe('COMA');
    expect(sugerirSeparador('')).toBe('COMA');
  });

  it('ignora las líneas vacías y mira solo las primeras', () => {
    const texto = [
      '',
      'a;b',
      '1;2',
      '3;4',
      '5;6',
      '7;8',
      ...Array.from({ length: 50 }, () => 'solo,una,coma,aqui'),
    ].join('\n');

    expect(sugerirSeparador(texto)).toBe('PUNTO_Y_COMA');
  });
});

describe('sugerirEncabezado', () => {
  it('una primera fila de texto y una segunda con fecha o número es encabezado', () => {
    expect(
      sugerirEncabezado([
        ['Fecha', 'Descripción', 'Monto'],
        ['13/10/2026', 'Cafe', '-12'],
      ]),
    ).toBe(true);
  });

  it('una primera fila con una fecha o un número no es encabezado', () => {
    expect(
      sugerirEncabezado([
        ['13/10/2026', 'Cafe', '-12'],
        ['14/10/2026', 'Té', '-3'],
      ]),
    ).toBe(false);
  });

  it('con un solo registro, es encabezado si no tiene ni fechas ni números', () => {
    expect(sugerirEncabezado([['Fecha', 'Descripción']])).toBe(true);
    expect(sugerirEncabezado([['13/10/2026', 'Cafe']])).toBe(false);
  });

  it('sin registros no hay encabezado', () => {
    expect(sugerirEncabezado([])).toBe(false);
  });
});

describe('cabeEnFormato', () => {
  it('exige los ceros del servidor', () => {
    expect(cabeEnFormato('05/06/2026', 'dd/MM/yyyy')).toBe(true);
    expect(cabeEnFormato('5/6/2026', 'dd/MM/yyyy')).toBe(false);
  });

  it('distingue día y mes', () => {
    expect(cabeEnFormato('25/12/2026', 'dd/MM/yyyy')).toBe(true);
    expect(cabeEnFormato('25/12/2026', 'MM/dd/yyyy')).toBe(false);
    expect(cabeEnFormato('12/25/2026', 'MM/dd/yyyy')).toBe(true);
  });

  it('los cuatro formatos', () => {
    expect(cabeEnFormato('2026-10-13', 'yyyy-MM-dd')).toBe(true);
    expect(cabeEnFormato('13-10-2026', 'dd-MM-yyyy')).toBe(true);
    expect(cabeEnFormato('13-10-2026', 'yyyy-MM-dd')).toBe(false);
  });
});

describe('sugerirFormatoFecha', () => {
  const ambiguas = [
    ['05/06/2026', 'a'],
    ['07/08/2026', 'b'],
  ];

  it('con fechas ambiguas gana el orden de la región', () => {
    expect(sugerirFormatoFecha(ambiguas, 'es-BO')).toBe('dd/MM/yyyy');
    expect(sugerirFormatoFecha(ambiguas, 'en-US')).toBe('MM/dd/yyyy');
  });

  it('una fecha que solo cabe en un formato lo decide aunque la región diga otro', () => {
    expect(sugerirFormatoFecha([['25/12/2026']], 'en-US')).toBe('dd/MM/yyyy');
    expect(sugerirFormatoFecha([['12/25/2026']], 'es-BO')).toBe('MM/dd/yyyy');
  });

  it('ISO y con guiones', () => {
    expect(sugerirFormatoFecha([['2026-10-13']], 'es-BO')).toBe('yyyy-MM-dd');
    expect(sugerirFormatoFecha([['13-10-2026']], 'en-US')).toBe('dd-MM-yyyy');
  });

  it('busca la columna de fecha entre varias', () => {
    expect(sugerirFormatoFecha([['Cafe', '25/12/2026', '-1']], 'en-US')).toBe('dd/MM/yyyy');
  });

  it('sin ninguna columna con fechas usa el de la región', () => {
    expect(sugerirFormatoFecha([['a', 'b']], 'es-BO')).toBe('dd/MM/yyyy');
    expect(sugerirFormatoFecha([], 'en-US')).toBe('MM/dd/yyyy');
  });
});

describe('sugerirDecimal', () => {
  it('con punto de miles y coma decimal', () => {
    expect(sugerirDecimal([['1.234,50']], 'en-US')).toBe('COMA');
  });

  it('con coma de miles y punto decimal', () => {
    expect(sugerirDecimal([['1,234.50']], 'es-BO')).toBe('PUNTO');
  });

  it('con un solo separador seguido de uno o dos dígitos', () => {
    expect(sugerirDecimal([['1234,5']], 'en-US')).toBe('COMA');
    expect(sugerirDecimal([['1234.5']], 'es-BO')).toBe('PUNTO');
  });

  it('sin pistas o con empate usa el de la región', () => {
    expect(sugerirDecimal([['1000', 'Cafe']], 'es-BO')).toBe('COMA');
    expect(sugerirDecimal([['1000', 'Cafe']], 'en-US')).toBe('PUNTO');
    expect(sugerirDecimal([['1,5', '1.5']], 'es-BO')).toBe('COMA');
  });

  it('no confunde las fechas con montos', () => {
    expect(sugerirDecimal([['13/10/2026', '2026-10-13']], 'en-US')).toBe('PUNTO');
  });
});

describe('sugerirMapeo', () => {
  const texto = [
    'Fecha;Descripción;Monto',
    '13/10/2026;"Pizzería, Marco";-1.234,50',
    '14/10/2026;Café;-3,25',
  ].join('\n');

  it('propone las cuatro cosas para un extracto con punto y coma', () => {
    expect(sugerirMapeo(texto, 'es-BO')).toEqual({
      separador: 'PUNTO_Y_COMA',
      tieneEncabezado: true,
      formatoFecha: 'dd/MM/yyyy',
      separadorDecimal: 'COMA',
    });
  });

  it('con un archivo grande también sugiere, leyendo solo las primeras filas', () => {
    const filas = Array.from({ length: 5000 }, (_, i) => `13/10/2026,Fila ${i},-1.5`);

    expect(sugerirMapeo(['fecha,desc,monto', ...filas].join('\n'), 'en-US')).toEqual({
      separador: 'COMA',
      tieneEncabezado: true,
      formatoFecha: 'dd/MM/yyyy',
      separadorDecimal: 'PUNTO',
    });
  });

  it('un archivo sin encabezado lo detecta', () => {
    expect(sugerirMapeo('13/10/2026,Cafe,-12.5\n14/10/2026,Te,-3', 'en-US').tieneEncabezado).toBe(
      false,
    );
  });

  it('una comilla sin cerrar no rompe las sugerencias', () => {
    expect(() => sugerirMapeo('a,"b\nc', 'es-BO')).not.toThrow();
  });
});
