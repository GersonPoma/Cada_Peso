import { describe, expect, it } from 'vitest';
import {
  MAXIMO_BYTES,
  MAXIMO_FILAS,
  analizarTexto,
  cargarArchivo,
  decodificarUtf8,
  validarTamano,
} from './validacion-archivo';

const codificar = (texto: string): Uint8Array => new TextEncoder().encode(texto);

/** Un CSV con encabezado y `n` filas de datos. */
function csv(filasDeDatos: number, conEncabezado = true, relleno = ''): string {
  const filas = Array.from(
    { length: filasDeDatos },
    (_, i) => `13/10/2026,Fila ${i},-1,5${relleno}`,
  );
  return [...(conEncabezado ? ['fecha,descripcion,monto'] : []), ...filas].join('\n');
}

describe('validarTamano', () => {
  it('0 bytes es un archivo vacío', () => {
    expect(validarTamano(0)).toBe('VACIO');
  });

  it('2 097 152 bytes (2 MiB) se acepta y 2 097 153 se rechaza', () => {
    expect(MAXIMO_BYTES).toBe(2_097_152);
    expect(validarTamano(MAXIMO_BYTES)).toBeNull();
    expect(validarTamano(MAXIMO_BYTES + 1)).toBe('TAMANO');
  });

  it('un archivo pequeño es válido', () => {
    expect(validarTamano(1)).toBeNull();
  });
});

describe('decodificarUtf8 (casos de DecodificadorUtf8Test del backend)', () => {
  it('decodifica UTF-8 con acentos', () => {
    expect(decodificarUtf8(codificar('Café ñandú'))).toBe('Café ñandú');
  });

  it('descarta el BOM', () => {
    const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...codificar('fecha,monto')]);

    expect(decodificarUtf8(bytes)).toBe('fecha,monto');
  });

  it('ISO-8859-1 con acentos no es UTF-8', () => {
    expect(decodificarUtf8(new Uint8Array([0x43, 0x61, 0x66, 0xe9]))).toBeNull();
  });

  it('UTF-16 con BOM no es UTF-8', () => {
    const bytes = new Uint8Array([0xff, 0xfe, 0x66, 0x00, 0x65, 0x00, 0x63, 0x00]);

    expect(decodificarUtf8(bytes)).toBeNull();
  });

  it('UTF-16 sin BOM se rechaza por los NUL', () => {
    const bytes = new Uint8Array([0x66, 0x00, 0x65, 0x00, 0x63, 0x00, 0x68, 0x00, 0x61, 0x00]);

    expect(decodificarUtf8(bytes)).toBeNull();
  });

  it('una secuencia UTF-8 truncada no es válida', () => {
    expect(decodificarUtf8(new Uint8Array([0x61, 0xc3]))).toBeNull();
  });

  it('un archivo ASCII es válido como UTF-8', () => {
    expect(decodificarUtf8(codificar('a,b\n1,2'))).toBe('a,b\n1,2');
  });

  it('solo el BOM decodifica como texto vacío', () => {
    expect(decodificarUtf8(new Uint8Array([0xef, 0xbb, 0xbf]))).toBe('');
  });
});

describe('analizarTexto: filas de datos y errores', () => {
  it('cuenta las filas de datos sin el encabezado', () => {
    const analisis = analizarTexto(csv(12), ',', true);

    expect(analisis.error).toBeNull();
    expect(analisis.filasDeDatos).toBe(12);
    expect(analisis.registros).toHaveLength(13);
  });

  it('con encabezado: 5000 filas de datos pasan y 5001 no', () => {
    expect(MAXIMO_FILAS).toBe(5000);
    expect(analizarTexto(csv(5000), ',', true)).toMatchObject({
      error: null,
      filasDeDatos: 5000,
    });
    expect(analizarTexto(csv(5001), ',', true).error).toBe('DEMASIADAS_FILAS');
  });

  it('sin encabezado: 5000 filas pasan y 5001 no', () => {
    expect(analizarTexto(csv(5000, false), ',', false)).toMatchObject({
      error: null,
      filasDeDatos: 5000,
    });
    expect(analizarTexto(csv(5001, false), ',', false).error).toBe('DEMASIADAS_FILAS');
  });

  it('las líneas vacías no cuentan para el tope', () => {
    const conVacias = csv(5000)
      .split('\n')
      .flatMap((linea, i) => (i % 250 === 0 ? [linea, ''] : [linea]));

    const analisis = analizarTexto(conVacias.join('\n'), ',', true);

    expect(conVacias.filter((l) => l === '').length).toBeGreaterThanOrEqual(20);
    expect(analisis).toMatchObject({ error: null, filasDeDatos: 5000 });
  });

  it('una comilla sin cerrar es un error', () => {
    expect(analizarTexto('fecha,desc\n13/10/2026,"abc', ',', true).error).toBe(
      'COMILLA_SIN_CERRAR',
    );
  });

  it('un archivo con solo el encabezado no tiene filas de datos', () => {
    expect(analizarTexto('fecha,descripcion,monto', ',', true).error).toBe('VACIO');
  });

  it('un archivo sin encabezado con una fila sí tiene datos', () => {
    expect(analizarTexto('13/10/2026,Cafe,-12', ',', false)).toMatchObject({
      error: null,
      filasDeDatos: 1,
    });
  });

  it('texto vacío (como un archivo con solo el BOM) no tiene filas de datos', () => {
    expect(analizarTexto('', ',', true).error).toBe('VACIO');
    expect(analizarTexto('', ',', false).error).toBe('VACIO');
  });

  it('el mismo texto se cuenta distinto con otro separador (las comillas dependen de él)', () => {
    const texto = '1;"a\nb";c';

    expect(analizarTexto(texto, ';', false).filasDeDatos).toBe(1);
    expect(analizarTexto(texto, ',', false).filasDeDatos).toBe(2);
  });

  it('una comilla sin cerrar depende del separador', () => {
    const texto = '1;"a';

    expect(analizarTexto(texto, ';', false).error).toBe('COMILLA_SIN_CERRAR');
    expect(analizarTexto(texto, ',', false).error).toBeNull();
  });

  it('las columnas son las del registro más ancho de las primeras filas', () => {
    expect(analizarTexto('a,b\n1,2,3,4\n5', ',', true).columnas).toBe(4);
  });

  it('solo mira las primeras 10 filas para contar columnas', () => {
    const filas = [...Array.from({ length: 10 }, () => 'a,b'), 'a,b,c,d,e'].join('\n');

    expect(analizarTexto(filas, ',', false).columnas).toBe(2);
  });
});

describe('cargarArchivo', () => {
  const archivo = (partes: BlobPart[], nombre = 'extracto.csv') => new File(partes, nombre);

  it('un archivo de 2 097 152 bytes se acepta y se decodifica', async () => {
    const resultado = await cargarArchivo(archivo([new Uint8Array(MAXIMO_BYTES).fill(97)]));

    expect('texto' in resultado).toBe(true);
    if ('texto' in resultado) {
      expect(resultado.texto).toHaveLength(MAXIMO_BYTES);
    }
  });

  it('un archivo de 2 097 153 bytes se rechaza sin leerlo', async () => {
    const resultado = await cargarArchivo(archivo([new Uint8Array(MAXIMO_BYTES + 1)]));

    expect(resultado).toEqual({ error: 'TAMANO' });
  });

  it('un archivo de 0 bytes está vacío', async () => {
    expect(await cargarArchivo(archivo([]))).toEqual({ error: 'VACIO' });
  });

  it('ISO-8859-1 y UTF-16 no son UTF-8', async () => {
    expect(await cargarArchivo(archivo([new Uint8Array([0x43, 0x61, 0x66, 0xe9])]))).toEqual({
      error: 'NO_UTF8',
    });
    expect(
      await cargarArchivo(archivo([new Uint8Array([0xff, 0xfe, 0x66, 0x00, 0x65, 0x00])])),
    ).toEqual({ error: 'NO_UTF8' });
  });

  it('UTF-8 con BOM se acepta y el BOM no forma parte del primer campo', async () => {
    const resultado = await cargarArchivo(
      archivo([new Uint8Array([0xef, 0xbb, 0xbf]), 'fecha,monto\n1,2']),
    );

    expect(resultado).toEqual({ texto: 'fecha,monto\n1,2' });
  });

  it('un archivo de 2 MiB o menos con 5000 filas se acepta y se cuentan sus filas', async () => {
    const texto = csv(5000, true, 'x'.repeat(380));
    expect(new TextEncoder().encode(texto).length).toBeLessThanOrEqual(MAXIMO_BYTES);

    const resultado = await cargarArchivo(archivo([texto]));

    expect('texto' in resultado).toBe(true);
    if ('texto' in resultado) {
      expect(analizarTexto(resultado.texto, ',', true)).toMatchObject({
        error: null,
        filasDeDatos: 5000,
      });
    }
  });
});
