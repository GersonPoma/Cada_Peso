import { describe, expect, it } from 'vitest';
import { ContextoCategoriaRecordada, categoriaRecordada } from './categoria-recordada';

const base: ContextoCategoriaRecordada = {
  creando: true,
  dividida: false,
  categoriaPristine: true,
  categoriaActual: null,
  sugerida: 7,
  idsExistentes: new Set([7, 8]),
};

describe('categoriaRecordada', () => {
  it('rellena al crear con la categoría vacía y sin tocar', () => {
    expect(categoriaRecordada(base)).toBe(7);
  });

  it('rellena con una categoría oculta si está en el árbol', () => {
    // El árbol del diálogo incluye las ocultas: basta con que el id exista.
    expect(categoriaRecordada({ ...base, sugerida: 8 })).toBe(8);
  });

  it('no rellena en modo Dividir', () => {
    expect(categoriaRecordada({ ...base, dividida: true })).toBeNull();
  });

  it('no rellena si la persona ya eligió una categoría', () => {
    expect(
      categoriaRecordada({ ...base, categoriaPristine: false, categoriaActual: 8 }),
    ).toBeNull();
  });

  it('no rellena si la categoría se tocó y se vació', () => {
    expect(categoriaRecordada({ ...base, categoriaPristine: false })).toBeNull();
  });

  it('no rellena al editar', () => {
    expect(categoriaRecordada({ ...base, creando: false })).toBeNull();
  });

  it('no rellena sin categoría predeterminada', () => {
    expect(categoriaRecordada({ ...base, sugerida: null })).toBeNull();
  });

  it('no rellena si la categoría ya no existe', () => {
    expect(categoriaRecordada({ ...base, sugerida: 99 })).toBeNull();
  });
});
