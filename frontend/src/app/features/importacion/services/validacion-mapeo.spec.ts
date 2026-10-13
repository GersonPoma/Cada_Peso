import { describe, expect, it } from 'vitest';
import { MapeoImportacion } from '../models/mapeo-importacion.model';
import { columnaAIndice, indiceAColumna, recortarColumnas, validarMapeo } from './validacion-mapeo';

function mapeo(cambios: Partial<MapeoImportacion> = {}): MapeoImportacion {
  return {
    separador: 'COMA',
    tieneEncabezado: true,
    columnaFecha: 0,
    formatoFecha: 'dd/MM/yyyy',
    modoMonto: 'UNA_COLUMNA',
    columnaMonto: 2,
    columnaDebito: null,
    columnaCredito: null,
    separadorDecimal: 'COMA',
    separadorMiles: 'PUNTO',
    columnaDescripcion: 1,
    columnaMemo: null,
    ...cambios,
  };
}

describe('validarMapeo', () => {
  it('un mapeo completo no tiene errores', () => {
    expect(validarMapeo(mapeo())).toEqual([]);
  });

  it('el separador de miles igual al decimal es un error', () => {
    expect(validarMapeo(mapeo({ separadorDecimal: 'COMA', separadorMiles: 'COMA' }))).toEqual([
      'MILES_IGUAL_DECIMAL',
    ]);
    expect(validarMapeo(mapeo({ separadorDecimal: 'PUNTO', separadorMiles: 'PUNTO' }))).toEqual([
      'MILES_IGUAL_DECIMAL',
    ]);
  });

  it('sin separador de miles o con ESPACIO no choca con el decimal', () => {
    expect(validarMapeo(mapeo({ separadorMiles: 'NINGUNO' }))).toEqual([]);
    expect(validarMapeo(mapeo({ separadorMiles: 'ESPACIO' }))).toEqual([]);
  });

  it('falta la columna de la fecha', () => {
    expect(validarMapeo(mapeo({ columnaFecha: null }))).toEqual(['FALTA_FECHA']);
  });

  it('falta la columna de la descripción', () => {
    expect(validarMapeo(mapeo({ columnaDescripcion: null }))).toEqual(['FALTA_DESCRIPCION']);
  });

  it('falta la columna del monto cuando va en una sola columna', () => {
    expect(validarMapeo(mapeo({ columnaMonto: null }))).toEqual(['FALTA_MONTO']);
  });

  it('en débito y crédito hacen falta las dos columnas', () => {
    const base = mapeo({ modoMonto: 'DEBITO_CREDITO', columnaMonto: null });

    expect(validarMapeo({ ...base, columnaDebito: 3, columnaCredito: 4 })).toEqual([]);
    expect(validarMapeo({ ...base, columnaDebito: 3, columnaCredito: null })).toEqual([
      'FALTA_DEBITO_CREDITO',
    ]);
    expect(validarMapeo({ ...base, columnaDebito: null, columnaCredito: 4 })).toEqual([
      'FALTA_DEBITO_CREDITO',
    ]);
    expect(validarMapeo(base)).toEqual(['FALTA_DEBITO_CREDITO']);
  });

  it('en débito y crédito no se exige columnaMonto ni en una columna las de débito y crédito', () => {
    expect(
      validarMapeo(mapeo({ modoMonto: 'DEBITO_CREDITO', columnaDebito: 1, columnaCredito: 2 })),
    ).toEqual([]);
    expect(validarMapeo(mapeo({ columnaDebito: null, columnaCredito: null }))).toEqual([]);
  });

  it('junta varios errores', () => {
    expect(
      validarMapeo(mapeo({ columnaFecha: null, columnaDescripcion: null, columnaMonto: null })),
    ).toEqual(['FALTA_FECHA', 'FALTA_DESCRIPCION', 'FALTA_MONTO']);
  });
});

describe('índices de columna: la interfaz desde 1 y el servidor desde 0', () => {
  it('ida y vuelta', () => {
    expect(indiceAColumna(0)).toBe(1);
    expect(columnaAIndice(1)).toBe(0);
    for (const indice of [0, 1, 7, 99]) {
      expect(columnaAIndice(indiceAColumna(indice))).toBe(indice);
    }
  });
});

describe('recortarColumnas', () => {
  it('sin columnas fuera de rango devuelve el mismo objeto', () => {
    const original = mapeo();

    expect(recortarColumnas(original, 4)).toBe(original);
  });

  it('anula las columnas que ya no existen y conserva las demás', () => {
    const recortado = recortarColumnas(
      mapeo({ columnaFecha: 0, columnaDescripcion: 1, columnaMonto: 3, columnaMemo: 5 }),
      3,
    );

    expect(recortado.columnaFecha).toBe(0);
    expect(recortado.columnaDescripcion).toBe(1);
    expect(recortado.columnaMonto).toBeNull();
    expect(recortado.columnaMemo).toBeNull();
  });

  it('no toca los campos que no son columnas ni muta la entrada', () => {
    const original = mapeo({ columnaMonto: 9 });

    const recortado = recortarColumnas(original, 2);

    expect(original.columnaMonto).toBe(9);
    expect(recortado.separador).toBe('COMA');
    expect(recortado.formatoFecha).toBe('dd/MM/yyyy');
  });

  it('el límite es exclusivo: con 3 columnas el índice 3 ya no existe', () => {
    expect(recortarColumnas(mapeo({ columnaMonto: 2 }), 3).columnaMonto).toBe(2);
    expect(recortarColumnas(mapeo({ columnaMonto: 3 }), 3).columnaMonto).toBeNull();
  });
});
