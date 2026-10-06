import { FormControl } from '@angular/forms';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mayorDeEdad } from './mayor-de-edad.validator';

declare const process: { env: Record<string, string | undefined> };

/** Zona horaria que fija `src/test-setup.ts` para toda la suite. */
const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

describe.each(['America/New_York', 'Asia/Tokyo'])('mayorDeEdad en %s', (zona) => {
  const validador = mayorDeEdad();

  /** Fija "hoy" (solo `Date`, para no frenar la estabilidad de Angular). */
  function hoyEs(anio: number, mes: number, dia: number): void {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(anio, mes - 1, dia, 12));
  }

  function error(anio: number, mes: number, dia: number) {
    return validador(new FormControl(new Date(anio, mes - 1, dia)));
  }

  beforeEach(() => {
    process.env['TZ'] = zona;
  });

  afterEach(() => {
    vi.useRealTimers();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  it('acepta a quien cumple 18 hoy', () => {
    hoyEs(2026, 10, 6);

    expect(error(2008, 10, 6)).toBeNull();
  });

  it('rechaza a quien cumple 18 mañana', () => {
    hoyEs(2026, 10, 6);

    expect(error(2008, 10, 7)).toEqual({ mayorDeEdad: { edadMinima: 18 } });
  });

  it('rechaza una fecha futura', () => {
    hoyEs(2026, 10, 6);

    expect(error(2030, 1, 1)).toEqual({ mayorDeEdad: { edadMinima: 18 } });
  });

  it('rechaza a quien nació un 29 de febrero el 28 de febrero de un año no bisiesto', () => {
    hoyEs(2026, 2, 28);

    expect(error(2008, 2, 29)).toEqual({ mayorDeEdad: { edadMinima: 18 } });
  });

  it('acepta a quien nació un 29 de febrero el 1 de marzo de un año no bisiesto', () => {
    hoyEs(2026, 3, 1);

    expect(error(2008, 2, 29)).toBeNull();
  });

  it('acepta null y un Date inválido', () => {
    hoyEs(2026, 10, 6);

    expect(validador(new FormControl(null))).toBeNull();
    expect(validador(new FormControl(new Date('no es una fecha')))).toBeNull();
  });
});
