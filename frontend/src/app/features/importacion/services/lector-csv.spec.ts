import { describe, expect, it } from 'vitest';
import { ErrorLectorCsv, leerRegistros } from './lector-csv';

/*
 * Los casos de esta tabla son los de `LectorCsvTest` del backend (commit 7c9ed9d, 2026-10-08):
 * `casos()` y los tests `unaComillaSinCerrarEsUn400`, `cortaAlSuperarElMaximoDeRegistros` y
 * `laLineaVaciaNoCuentaParaElMaximo`. Si el servidor cambia una regla de lectura, se actualizan
 * juntos: el cliente debe contar igual que el servidor.
 */
const CASOS: [string, string, string, string[][]][] = [
  ['un registro', 'a,b,c', ',', [['a', 'b', 'c']]],
  [
    'LF',
    'a,b\nc,d',
    ',',
    [
      ['a', 'b'],
      ['c', 'd'],
    ],
  ],
  [
    'CRLF con salto final',
    'a,b\r\nc,d\r\n',
    ',',
    [
      ['a', 'b'],
      ['c', 'd'],
    ],
  ],
  [
    'CR solo',
    'a,b\rc,d',
    ',',
    [
      ['a', 'b'],
      ['c', 'd'],
    ],
  ],
  ['punto y coma', 'a;b;c', ';', [['a', 'b', 'c']]],
  ['tabulador', 'a\tb', '\t', [['a', 'b']]],
  ['separador entre comillas', '"a,b",c', ',', [['a,b', 'c']]],
  ['comillas escapadas', '"di ""hola""",x', ',', [['di "hola"', 'x']]],
  ['LF entre comillas', '"uno\ndos",x', ',', [['uno\ndos', 'x']]],
  ['CRLF entre comillas', '"uno\r\ndos",x', ',', [['uno\r\ndos', 'x']]],
  ['campo vacío en medio', 'a,,c', ',', [['a', '', 'c']]],
  ['campo final vacío', 'a,b,', ',', [['a', 'b', '']]],
  ['solo el separador', ',', ',', [['', '']]],
  ['comillado vacío', '"",x', ',', [['', 'x']]],
  [
    'líneas vacías ignoradas',
    'a,b\n\n\nc,d\n',
    ',',
    [
      ['a', 'b'],
      ['c', 'd'],
    ],
  ],
  ['texto vacío', '', ',', []],
  ['solo saltos', '\n\r\n', ',', []],
  ['comilla en medio del campo', 'ab"cd,e', ',', [['ab"cd', 'e']]],
  ['texto tras la comilla de cierre', '"ab"cd,e', ',', [['abcd', 'e']]],
];

describe('leerRegistros: casos del backend (LectorCsvTest.casos)', () => {
  it.each(CASOS)('%s', (_nombre, texto, separador, esperado) => {
    expect(leerRegistros(texto, separador, 100)).toEqual(esperado);
  });
});

describe('leerRegistros: errores y tope de registros (LectorCsvTest)', () => {
  it('una comilla sin cerrar es un error de lectura', () => {
    for (const texto of ['a,"b\nc', '"']) {
      const error = (() => {
        try {
          leerRegistros(texto, ',', 100);
        } catch (e) {
          return e;
        }
        return null;
      })();

      expect(error).toBeInstanceOf(ErrorLectorCsv);
      expect((error as ErrorLectorCsv).motivo).toBe('COMILLA_SIN_CERRAR');
    }
  });

  it('corta al superar el máximo de registros', () => {
    expect(leerRegistros('1\n2\n3', ',', 3)).toHaveLength(3);
    expect(() => leerRegistros('1\n2\n3\n4', ',', 3)).toThrowError(ErrorLectorCsv);
    try {
      leerRegistros('1\n2\n3\n4', ',', 3);
    } catch (e) {
      expect((e as ErrorLectorCsv).motivo).toBe('DEMASIADOS_REGISTROS');
    }
  });

  it('la línea vacía no cuenta para el máximo', () => {
    expect(leerRegistros('1\n\n2\n\n', ',', 2)).toHaveLength(2);
  });
});

describe('leerRegistros: otros bordes del cliente', () => {
  it('soloPrimeros devuelve los primeros registros sin error aunque haya más', () => {
    const texto = Array.from({ length: 50 }, (_, i) => `${i},x`).join('\n');

    expect(leerRegistros(texto, ',', 3, true)).toEqual([
      ['0', 'x'],
      ['1', 'x'],
      ['2', 'x'],
    ]);
    expect(() => leerRegistros(texto, ',', 3)).toThrowError(ErrorLectorCsv);
  });

  it('soloPrimeros con menos registros que el pedido devuelve todos', () => {
    expect(leerRegistros('a,b\nc,d', ',', 10, true)).toEqual([
      ['a', 'b'],
      ['c', 'd'],
    ]);
  });

  it('sin máximo lee todo el texto', () => {
    const texto = Array.from({ length: 20000 }, (_, i) => `${i},x`).join('\n');

    expect(leerRegistros(texto, ',')).toHaveLength(20000);
  });

  it('una celda entre comillas con comas, comillas y saltos es un solo campo', () => {
    expect(leerRegistros('"Pizzería, Marco ""Polo""",135', ',')).toEqual([
      ['Pizzería, Marco "Polo"', '135'],
    ]);
  });

  it('el separador del tabulador no parte campos con comas', () => {
    expect(leerRegistros('a,b\tc', '\t')).toEqual([['a,b', 'c']]);
  });

  it('el mismo texto se lee distinto con otro separador (las comillas dependen de él)', () => {
    const texto = 'a;"b,c";d';

    expect(leerRegistros(texto, ';')).toEqual([['a', 'b,c', 'd']]);
    expect(leerRegistros(texto, ',')).toEqual([['a;"b', 'c";d']]);
  });

  it('un archivo de 2 MiB con una sola celda entre comillas es un registro', () => {
    const texto = `"${'a'.repeat(2 * 1024 * 1024 - 2)}"`;

    const registros = leerRegistros(texto, ',');

    expect(registros).toHaveLength(1);
    expect(registros[0][0]).toHaveLength(2 * 1024 * 1024 - 2);
  });

  it('un salto CR seguido de LF cuenta una sola vez, pero CR CR cuenta dos líneas vacías', () => {
    expect(leerRegistros('a\r\nb', ',')).toEqual([['a'], ['b']]);
    expect(leerRegistros('a\r\rb', ',')).toEqual([['a'], ['b']]);
  });
});
