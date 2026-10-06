import { describe, expect, it } from 'vitest';
import { PaginadorIntl } from './paginador-intl';

describe('PaginadorIntl', () => {
  const intl = new PaginadorIntl();

  it('muestra el rango de una página intermedia', () => {
    expect(intl.getRangeLabel(1, 10, 25)).toBe('11 – 20 de 25');
  });

  it('acota la última página al total de elementos', () => {
    expect(intl.getRangeLabel(2, 10, 25)).toBe('21 – 25 de 25');
  });

  it('muestra 0 de 0 para una lista vacía', () => {
    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 de 0');
  });

  it('tiene en español la etiqueta de elementos por página y las de navegación', () => {
    expect(intl.itemsPerPageLabel).toBe('Elementos por página:');
    expect(intl.firstPageLabel).toBe('Primera página');
    expect(intl.previousPageLabel).toBe('Página anterior');
    expect(intl.nextPageLabel).toBe('Página siguiente');
    expect(intl.lastPageLabel).toBe('Última página');
  });
});
