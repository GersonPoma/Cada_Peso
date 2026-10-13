/*
 * Mapeo del CSV: los valores que el backend admite en el formulario multipart. `separador`,
 * `separadorDecimal` y `separadorMiles` viajan por el NOMBRE del enum; `formatoFecha`, por su
 * ETIQUETA (`dd/MM/yyyy`).
 */

export const SEPARADORES = ['COMA', 'PUNTO_Y_COMA', 'TABULADOR'] as const;
export type Separador = (typeof SEPARADORES)[number];

export const ETIQUETAS_SEPARADOR: Readonly<Record<Separador, string>> = {
  COMA: 'Coma (,)',
  PUNTO_Y_COMA: 'Punto y coma (;)',
  TABULADOR: 'Tabulador',
};

/** El carácter de cada separador, para leer el archivo en el navegador. */
export const CARACTER_SEPARADOR: Readonly<Record<Separador, string>> = {
  COMA: ',',
  PUNTO_Y_COMA: ';',
  TABULADOR: '\t',
};

export const FORMATOS_FECHA = ['yyyy-MM-dd', 'dd/MM/yyyy', 'MM/dd/yyyy', 'dd-MM-yyyy'] as const;
export type FormatoFecha = (typeof FORMATOS_FECHA)[number];

export const SEPARADORES_DECIMAL = ['PUNTO', 'COMA'] as const;
export type SeparadorDecimal = (typeof SEPARADORES_DECIMAL)[number];

export const ETIQUETAS_SEPARADOR_DECIMAL: Readonly<Record<SeparadorDecimal, string>> = {
  PUNTO: 'Punto (1234.50)',
  COMA: 'Coma (1234,50)',
};

export const SEPARADORES_MILES = ['NINGUNO', 'PUNTO', 'COMA', 'ESPACIO'] as const;
export type SeparadorMiles = (typeof SEPARADORES_MILES)[number];

export const ETIQUETAS_SEPARADOR_MILES: Readonly<Record<SeparadorMiles, string>> = {
  NINGUNO: 'Ninguno',
  PUNTO: 'Punto (1.234)',
  COMA: 'Coma (1,234)',
  ESPACIO: 'Espacio (1 234)',
};

/** El monto va en una columna con signo o en dos (débito y crédito), nunca mezclados. */
export type ModoMonto = 'UNA_COLUMNA' | 'DEBITO_CREDITO';

/**
 * El mapeo que la persona edita. Los índices de columna son los del servidor (desde 0): la
 * interfaz los muestra desde 1 (`indiceAColumna`/`columnaAIndice` en `validacion-mapeo.ts`).
 */
export interface MapeoImportacion {
  separador: Separador;
  tieneEncabezado: boolean;
  columnaFecha: number | null;
  formatoFecha: FormatoFecha;
  modoMonto: ModoMonto;
  columnaMonto: number | null;
  columnaDebito: number | null;
  columnaCredito: number | null;
  separadorDecimal: SeparadorDecimal;
  separadorMiles: SeparadorMiles;
  columnaDescripcion: number | null;
  columnaMemo: number | null;
}
