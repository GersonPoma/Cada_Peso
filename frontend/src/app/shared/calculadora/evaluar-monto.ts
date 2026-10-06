import { leerMonto } from '../formato/milliunits';
import { regionUsuario } from '../formato/region-usuario';

/*
 * Calculadora de montos: evalúa expresiones con + - * / y un signo - unario, y devuelve el
 * resultado en milésimas. Nada de eval, Function ni coma flotante: cada número se lee con
 * leerMonto (separadores de la región, hasta 3 decimales) y se opera como fracción exacta de
 * BigInt; solo el resultado final se redondea a milésimas, con la mitad alejada de cero.
 */

/** Fracción exacta `n / d`, con `d` siempre positivo. */
interface Fraccion {
  n: bigint;
  d: bigint;
}

type Operador = '+' | '-' | '*' | '/';

const OPERADORES = new Set<string>(['+', '-', '*', '/']);
const MILESIMAS = 1000n;
const MAXIMO_SEGURO = BigInt(Number.MAX_SAFE_INTEGER);

/**
 * Evalúa `texto` y devuelve el resultado en milésimas, o `null` si la expresión es inválida
 * (vacía, operador al final, dos operadores seguidos salvo un `-` unario, caracteres que no son
 * números ni operadores, división por cero, más de 3 decimales o resultado fuera del rango seguro).
 */
export function evaluarMonto(texto: string, region: string = regionUsuario()): number | null {
  const fichas = tokenizar(texto, region);
  if (!fichas) {
    return null;
  }
  const resultado = evaluar(fichas.numeros, fichas.operadores);
  return resultado ? aMilesimas(resultado) : null;
}

/**
 * Separa números y operadores. Un `-` al principio o después de un operador es el signo del
 * número siguiente (solo uno). Devuelve `null` si la expresión no está bien formada.
 */
function tokenizar(
  texto: string,
  region: string,
): { numeros: Fraccion[]; operadores: Operador[] } | null {
  const numeros: Fraccion[] = [];
  const operadores: Operador[] = [];
  let i = 0;
  const limpio = texto.trim();
  if (limpio === '') {
    return null;
  }
  while (i < limpio.length) {
    // Un número: espacios, signo opcional y luego todo hasta el próximo operador.
    while (limpio[i] === ' ') {
      i++;
    }
    let negativo = false;
    if (limpio[i] === '-') {
      negativo = true;
      i++;
    }
    let fin = i;
    while (fin < limpio.length && !OPERADORES.has(limpio[fin])) {
      fin++;
    }
    const numero = leerNumero(limpio.slice(i, fin), region);
    if (!numero) {
      return null;
    }
    numeros.push(negativo ? { n: -numero.n, d: numero.d } : numero);
    if (fin === limpio.length) {
      break;
    }
    operadores.push(limpio[fin] as Operador);
    i = fin + 1;
    if (i === limpio.length) {
      return null; // operador al final
    }
  }
  return { numeros, operadores };
}

/** Un operando sin signo, como fracción exacta de unidades; `null` si no es un número válido. */
function leerNumero(texto: string, region: string): Fraccion | null {
  const recortado = texto.trim();
  if (recortado === '' || recortado.startsWith('-') || recortado.startsWith('−')) {
    return null;
  }
  const lectura = leerMonto(recortado, region);
  if (lectura.estado !== 'valido') {
    return null;
  }
  return reducir({ n: BigInt(lectura.milliunits), d: MILESIMAS });
}

/** Aplica `*` y `/` de izquierda a derecha y luego `+` y `-`. `null` si se divide por cero. */
function evaluar(numeros: Fraccion[], operadores: Operador[]): Fraccion | null {
  const terminos: Fraccion[] = [numeros[0]];
  const sumas: Operador[] = [];
  for (let k = 0; k < operadores.length; k++) {
    const operador = operadores[k];
    const siguiente = numeros[k + 1];
    if (operador === '*' || operador === '/') {
      const anterior = terminos[terminos.length - 1];
      const producto =
        operador === '*' ? multiplicar(anterior, siguiente) : dividir(anterior, siguiente);
      if (!producto) {
        return null;
      }
      terminos[terminos.length - 1] = producto;
    } else {
      sumas.push(operador);
      terminos.push(siguiente);
    }
  }
  return terminos.reduce((total, termino, indice) =>
    indice === 0 ? total : sumar(total, sumas[indice - 1] === '+' ? termino : negar(termino)),
  );
}

function sumar(a: Fraccion, b: Fraccion): Fraccion {
  return reducir({ n: a.n * b.d + b.n * a.d, d: a.d * b.d });
}

function negar(a: Fraccion): Fraccion {
  return { n: -a.n, d: a.d };
}

function multiplicar(a: Fraccion, b: Fraccion): Fraccion {
  return reducir({ n: a.n * b.n, d: a.d * b.d });
}

function dividir(a: Fraccion, b: Fraccion): Fraccion | null {
  if (b.n === 0n) {
    return null;
  }
  const signo = b.n < 0n ? -1n : 1n;
  return reducir({ n: a.n * b.d * signo, d: a.d * b.n * signo });
}

function reducir(f: Fraccion): Fraccion {
  const divisor = mcd(f.n < 0n ? -f.n : f.n, f.d);
  return divisor > 1n ? { n: f.n / divisor, d: f.d / divisor } : f;
}

function mcd(a: bigint, b: bigint): bigint {
  while (b !== 0n) {
    [a, b] = [b, a % b];
  }
  return a === 0n ? 1n : a;
}

/** Redondea la fracción a milésimas (mitad alejada de cero); `null` fuera del rango seguro. */
function aMilesimas(f: Fraccion): number | null {
  const escalado = f.n * MILESIMAS;
  const absoluto = escalado < 0n ? -escalado : escalado;
  let cociente = absoluto / f.d;
  if ((absoluto % f.d) * 2n >= f.d) {
    cociente += 1n;
  }
  if (cociente > MAXIMO_SEGURO) {
    return null;
  }
  const valor = Number(cociente);
  return escalado < 0n && valor !== 0 ? -valor : valor;
}
