import { MapeoImportacion } from '../models/mapeo-importacion.model';

/**
 * Lo que impide pedir la vista previa con un mapeo. El backend responde un mapeo inválido con
 * `400 DATOS_INVALIDOS` sin mapa `errores`, así que se previene aquí; los textos viven en
 * `mensajes-importacion.ts`.
 */
export type ErrorMapeo =
  | 'MILES_IGUAL_DECIMAL'
  | 'FALTA_FECHA'
  | 'FALTA_DESCRIPCION'
  | 'FALTA_MONTO'
  | 'FALTA_DEBITO_CREDITO';

/** El número de columna que ve la persona (desde 1) a partir del índice del servidor (desde 0). */
export function indiceAColumna(indice: number): number {
  return indice + 1;
}

/** El índice del servidor (desde 0) a partir del número de columna que ve la persona. */
export function columnaAIndice(columna: number): number {
  return columna - 1;
}

/**
 * Las reglas del mapeo que el servidor aplica a los parámetros: el separador de miles distinto del
 * decimal (`NINGUNO` nunca choca), la fecha y la descripción obligatorias, y el monto en UNA
 * columna con signo o en DÉBITO y CRÉDITO (las dos), nunca mezclados.
 */
export function validarMapeo(mapeo: MapeoImportacion): ErrorMapeo[] {
  const errores: ErrorMapeo[] = [];
  if (mapeo.separadorMiles !== 'NINGUNO' && mapeo.separadorMiles === mapeo.separadorDecimal) {
    errores.push('MILES_IGUAL_DECIMAL');
  }
  if (mapeo.columnaFecha === null) {
    errores.push('FALTA_FECHA');
  }
  if (mapeo.columnaDescripcion === null) {
    errores.push('FALTA_DESCRIPCION');
  }
  if (mapeo.modoMonto === 'UNA_COLUMNA') {
    if (mapeo.columnaMonto === null) {
      errores.push('FALTA_MONTO');
    }
  } else if (mapeo.columnaDebito === null || mapeo.columnaCredito === null) {
    errores.push('FALTA_DEBITO_CREDITO');
  }
  return errores;
}

const CAMPOS_DE_COLUMNA = [
  'columnaFecha',
  'columnaMonto',
  'columnaDebito',
  'columnaCredito',
  'columnaDescripcion',
  'columnaMemo',
] as const;

/**
 * Anula las columnas que quedaron fuera de las detectadas (por ejemplo, al cambiar el separador y
 * quedar menos columnas): una columna que no existe no puede enviarse. Devuelve el mismo objeto si
 * no hay nada que anular.
 */
export function recortarColumnas(mapeo: MapeoImportacion, columnas: number): MapeoImportacion {
  const fuera = CAMPOS_DE_COLUMNA.filter((campo) => {
    const indice = mapeo[campo];
    return indice !== null && indice >= columnas;
  });
  if (fuera.length === 0) {
    return mapeo;
  }
  const recortado: MapeoImportacion = { ...mapeo };
  for (const campo of fuera) {
    recortado[campo] = null;
  }
  return recortado;
}
