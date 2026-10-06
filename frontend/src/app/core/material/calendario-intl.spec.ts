import { MatDatepickerIntl } from '@angular/material/datepicker';
import { describe, expect, it } from 'vitest';
import { CalendarioIntl } from './calendario-intl';

describe('CalendarioIntl', () => {
  const intl = new CalendarioIntl();

  it('tiene en español las etiquetas del botón y de los controles del calendario', () => {
    expect(intl.openCalendarLabel).toBe('Abrir calendario');
    expect(intl.prevMonthLabel).toBe('Mes anterior');
    expect(intl.nextMonthLabel).toBe('Mes siguiente');
    expect(intl.switchToMultiYearViewLabel).toBe('Elegir mes y año');
  });

  it('no deja ninguna etiqueta con el texto original en inglés', () => {
    const original = new MatDatepickerIntl() as unknown as Record<string, unknown>;
    const traducido = intl as unknown as Record<string, unknown>;
    const etiquetas = Object.keys(original).filter((clave) => typeof original[clave] === 'string');

    expect(etiquetas.length).toBeGreaterThan(0);
    for (const clave of etiquetas) {
      expect(traducido[clave], clave).not.toBe(original[clave]);
    }
  });

  it('formatea el rango de años en español', () => {
    expect(intl.formatYearRangeLabel('2016', '2039')).toBe('2016 a 2039');
  });
});
