import { regionUsuario } from './region-usuario';

/** Milésimas por unidad monetaria: el dinero viaja como entero (1.000 = 1 unidad). */
const DIGITOS_DECIMALES = 3;

/** Espacios especiales que algunas regiones usan como separador de miles (ej. `fr-FR`). */
const ESPACIOS_DE_MILES = [' ', ' '];

/** Resultado de leer un monto escrito: sin valor, válido (en milésimas) o inválido y por qué. */
export type LecturaMonto =
  | { estado: 'vacio' }
  | { estado: 'valido'; milliunits: number }
  | { estado: 'invalido'; motivo: 'formato' | 'decimales' | 'rango' };

interface Separadores {
  decimal: string;
  miles: string[];
}

/** Separadores decimal y de miles de la región, según `Intl.NumberFormat`. */
function separadores(region: string): Separadores {
  const partes = new Intl.NumberFormat(region).formatToParts(1234567.8);
  const decimal = partes.find((parte) => parte.type === 'decimal')?.value ?? '.';
  const grupo = partes.find((parte) => parte.type === 'group')?.value ?? ',';
  // Si la región agrupa con un espacio especial, también se acepta el espacio común.
  const miles = ESPACIOS_DE_MILES.includes(grupo) ? [grupo, ' '] : [grupo];
  return { decimal, miles };
}

/** Escapa un texto para usarlo literal dentro de una expresión regular. */
function escapar(texto: string): string {
  return texto.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Devuelve los dígitos de la parte entera sin separadores, o `null` si está mal agrupada. */
function digitosEnteros(entera: string, miles: string[]): string | null {
  if (/^\d*$/.test(entera)) {
    return entera;
  }
  const grupo = miles.map(escapar).join('|');
  const agrupada = new RegExp(`^\\d{1,3}((?:${grupo})\\d{3})+$`);
  return agrupada.test(entera) ? entera.replace(/\D/g, '') : null;
}

/**
 * Lee un monto escrito por la persona, con los separadores de su región, y lo convierte a un
 * entero en milésimas **sin aritmética de coma flotante**: separa signo, parte entera y parte
 * decimal y compone el entero a partir de los dígitos. Rechaza más de 3 decimales (aunque sobren
 * ceros), los separadores de miles mal agrupados, cualquier otro carácter y los valores que no
 * caben en un entero exacto. Un texto vacío es `vacio`, no un error.
 */
export function leerMonto(texto: string, region: string = regionUsuario()): LecturaMonto {
  let resto = texto.trim();
  if (resto === '') {
    return { estado: 'vacio' };
  }
  let negativo = false;
  if (resto.startsWith('-') || resto.startsWith('−')) {
    negativo = true;
    resto = resto.slice(1);
  }

  const { decimal, miles } = separadores(region);
  const partes = resto.split(decimal);
  if (partes.length > 2) {
    return { estado: 'invalido', motivo: 'formato' };
  }
  const [entera, decimales = ''] = partes;
  const digitos = digitosEnteros(entera, miles);
  if (digitos === null || !/^\d*$/.test(decimales) || (digitos === '' && decimales === '')) {
    return { estado: 'invalido', motivo: 'formato' };
  }
  if (decimales.length > DIGITOS_DECIMALES) {
    return { estado: 'invalido', motivo: 'decimales' };
  }

  // Lectura de una cadena de solo dígitos: es un entero exacto mientras sea seguro.
  const enMilesimas = (digitos + decimales.padEnd(DIGITOS_DECIMALES, '0')).replace(/^0+/, '');
  const valor = enMilesimas === '' ? 0 : Number(enMilesimas);
  if (!Number.isSafeInteger(valor)) {
    return { estado: 'invalido', motivo: 'rango' };
  }
  return { estado: 'valido', milliunits: negativo && valor !== 0 ? -valor : valor };
}

/** Milésimas de un monto escrito, o `null` si está vacío o es inválido (ver `leerMonto`). */
export function aMilliunits(texto: string, region: string = regionUsuario()): number | null {
  const lectura = leerMonto(texto, region);
  return lectura.estado === 'valido' ? lectura.milliunits : null;
}

/**
 * Texto editable de un monto en milésimas: separador decimal de la región, sin separador de
 * miles y sin ceros decimales sobrantes (ej. `1234567` → `1234,567` en `es-BO`). Solo usa
 * operaciones exactas sobre enteros.
 */
export function deMilliunits(milliunits: number, region: string = regionUsuario()): string {
  const absoluto = Math.abs(milliunits);
  const fraccion = absoluto % 1000;
  const entera = (absoluto - fraccion) / 1000;
  const decimales = String(fraccion).padStart(DIGITOS_DECIMALES, '0').replace(/0+$/, '');
  const signo = milliunits < 0 ? '-' : '';
  return decimales === ''
    ? `${signo}${entera}`
    : `${signo}${entera}${separadores(region).decimal}${decimales}`;
}
