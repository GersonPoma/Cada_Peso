import {
  CARACTER_SEPARADOR,
  FormatoFecha,
  SEPARADORES,
  Separador,
  SeparadorDecimal,
} from '../models/mapeo-importacion.model';
import { ErrorLectorCsv, leerRegistros } from './lector-csv';

/** Lo que el cliente propone, editable: nada se envía ni se aplica sin que la persona lo confirme. */
export interface SugerenciasMapeo {
  separador: Separador;
  tieneEncabezado: boolean;
  formatoFecha: FormatoFecha;
  separadorDecimal: SeparadorDecimal;
}

/** Líneas del archivo que se miran para sugerir el separador. */
const LINEAS_PARA_SEPARADOR = 5;

/** Registros de muestra para sugerir el encabezado, el formato de fecha y el decimal. */
const REGISTROS_DE_MUESTRA = 10;

/** El orden del día y el mes de la región: `dd/MM/yyyy` o `MM/dd/yyyy` (o ISO si empieza por año). */
export function formatoDeFechaRegional(region: string): FormatoFecha {
  try {
    const orden = new Intl.DateTimeFormat(region, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
      .formatToParts(new Date(2026, 10, 25))
      .map((parte) => parte.type)
      .filter((tipo) => tipo === 'day' || tipo === 'month' || tipo === 'year');
    if (orden[0] === 'year') {
      return 'yyyy-MM-dd';
    }
    return orden.indexOf('day') < orden.indexOf('month') ? 'dd/MM/yyyy' : 'MM/dd/yyyy';
  } catch {
    return 'MM/dd/yyyy';
  }
}

/** El separador decimal de la región. */
export function decimalDeLaRegion(region: string): SeparadorDecimal {
  try {
    const decimal = new Intl.NumberFormat(region)
      .formatToParts(1.1)
      .find((parte) => parte.type === 'decimal')?.value;
    return decimal === ',' ? 'COMA' : 'PUNTO';
  } catch {
    return 'PUNTO';
  }
}

/**
 * El separador de campos: de los tres candidatos, el que parte las primeras líneas en la misma
 * cantidad de campos (más de uno); si ninguno es consistente, el que da más campos en la línea
 * más corta. Empata en el orden coma, punto y coma, tabulador, y sin pistas es la coma.
 */
export function sugerirSeparador(texto: string): Separador {
  const muestra = texto
    .split(/\r\n|\n|\r/)
    .filter((linea) => linea.trim() !== '')
    .slice(0, LINEAS_PARA_SEPARADOR)
    .join('\n');
  let mejor: Separador = 'COMA';
  let mejorPuntaje = 0;
  for (const separador of SEPARADORES) {
    let registros: string[][];
    try {
      registros = leerRegistros(muestra, CARACTER_SEPARADOR[separador]);
    } catch (e) {
      if (e instanceof ErrorLectorCsv) {
        continue;
      }
      throw e;
    }
    if (registros.length === 0) {
      continue;
    }
    const anchos = registros.map((registro) => registro.length);
    const minimo = Math.min(...anchos);
    const consistente = anchos.every((ancho) => ancho === anchos[0]) && minimo > 1;
    const puntaje = consistente ? 1000 + minimo : minimo > 1 ? minimo : 0;
    if (puntaje > mejorPuntaje) {
      mejor = separador;
      mejorPuntaje = puntaje;
    }
  }
  return mejor;
}

const REGEX_NUMERO = /^[+-]?[\d.,\s]+$/;
const REGEX_FECHA_SUELTA = /^\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4}$/;

function parecenumeroOFecha(celda: string): boolean {
  const texto = celda.trim();
  return (REGEX_NUMERO.test(texto) && /\d/.test(texto)) || REGEX_FECHA_SUELTA.test(texto);
}

/**
 * Hay encabezado si el primer registro no tiene ninguna celda que parezca fecha o número y el
 * segundo sí. Con un solo registro, si no tiene ninguna.
 */
export function sugerirEncabezado(registros: string[][]): boolean {
  const [primero, segundo] = registros;
  if (!primero) {
    return false;
  }
  const primeroConDatos = primero.some(parecenumeroOFecha);
  if (primeroConDatos) {
    return false;
  }
  return segundo ? segundo.some(parecenumeroOFecha) : true;
}

function mesYDiaValidos(mes: number, dia: number): boolean {
  return mes >= 1 && mes <= 12 && dia >= 1 && dia <= 31;
}

/** Si la celda se lee en ese formato (con los ceros que exige el servidor). */
export function cabeEnFormato(celda: string, formato: FormatoFecha): boolean {
  const texto = celda.trim();
  switch (formato) {
    case 'yyyy-MM-dd': {
      const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
      return !!m && mesYDiaValidos(Number(m[2]), Number(m[3]));
    }
    case 'dd/MM/yyyy': {
      const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto);
      return !!m && mesYDiaValidos(Number(m[2]), Number(m[1]));
    }
    case 'MM/dd/yyyy': {
      const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto);
      return !!m && mesYDiaValidos(Number(m[1]), Number(m[2]));
    }
    case 'dd-MM-yyyy': {
      const m = /^(\d{2})-(\d{2})-(\d{4})$/.exec(texto);
      return !!m && mesYDiaValidos(Number(m[2]), Number(m[1]));
    }
  }
}

const TODOS_LOS_FORMATOS: FormatoFecha[] = ['yyyy-MM-dd', 'dd/MM/yyyy', 'MM/dd/yyyy', 'dd-MM-yyyy'];

/**
 * El formato de fecha: el de la primera columna cuyas celdas caben todas en algún formato; si
 * caben en más de uno (`05/06/2026`), gana el orden de la región. Sin ninguna, el de la región.
 */
export function sugerirFormatoFecha(datos: string[][], region: string): FormatoFecha {
  const regional = formatoDeFechaRegional(region);
  const columnas = datos.reduce((maximo, fila) => Math.max(maximo, fila.length), 0);
  for (let columna = 0; columna < columnas; columna++) {
    const celdas = datos
      .map((fila) => fila[columna])
      .filter((c) => c !== undefined && c.trim() !== '');
    if (celdas.length === 0) {
      continue;
    }
    const candidatos = TODOS_LOS_FORMATOS.filter((formato) =>
      celdas.every((celda) => cabeEnFormato(celda, formato)),
    );
    if (candidatos.length > 0) {
      return candidatos.includes(regional) ? regional : candidatos[0];
    }
  }
  return regional;
}

/**
 * El separador decimal: el que indican las celdas que parecen montos (el último separador de una
 * celda con punto y coma, o un solo separador seguido de uno o dos dígitos al final) y, si no hay
 * pistas, el de la región.
 */
export function sugerirDecimal(datos: string[][], region: string): SeparadorDecimal {
  let votosComa = 0;
  let votosPunto = 0;
  for (const fila of datos) {
    for (const celda of fila) {
      const texto = celda.trim();
      if (!REGEX_NUMERO.test(texto) || !/\d/.test(texto)) {
        continue;
      }
      const ultimaComa = texto.lastIndexOf(',');
      const ultimoPunto = texto.lastIndexOf('.');
      if (ultimaComa >= 0 && ultimoPunto >= 0) {
        if (ultimaComa > ultimoPunto) {
          votosComa++;
        } else {
          votosPunto++;
        }
      } else if (ultimaComa >= 0 && /,\d{1,2}$/.test(texto)) {
        votosComa++;
      } else if (ultimoPunto >= 0 && /\.\d{1,2}$/.test(texto)) {
        votosPunto++;
      }
    }
  }
  if (votosComa === votosPunto) {
    return decimalDeLaRegion(region);
  }
  return votosComa > votosPunto ? 'COMA' : 'PUNTO';
}

/** Las sugerencias para un archivo ya decodificado, con la región de la persona. */
export function sugerirMapeo(texto: string, region: string): SugerenciasMapeo {
  const separador = sugerirSeparador(texto);
  let registros: string[][] = [];
  try {
    registros = leerRegistros(texto, CARACTER_SEPARADOR[separador], REGISTROS_DE_MUESTRA, true);
  } catch (e) {
    if (!(e instanceof ErrorLectorCsv)) {
      throw e;
    }
    // Con más registros que la muestra, o con una comilla sin cerrar, no se sugiere nada más.
  }
  const tieneEncabezado = sugerirEncabezado(registros);
  const datos = tieneEncabezado ? registros.slice(1) : registros;
  return {
    separador,
    tieneEncabezado,
    formatoFecha: sugerirFormatoFecha(datos, region),
    separadorDecimal: sugerirDecimal(datos, region),
  };
}
