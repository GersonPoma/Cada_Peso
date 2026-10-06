import { describe, expect, it } from 'vitest';
import { ArbolCategorias } from '../models/arbol-categorias.model';
import { CategoriaResponse } from '../models/categoria-response.model';
import { GrupoCategoriaConCategoriasResponse } from '../models/grupo-categoria-con-categorias-response.model';
import { moverCategoria, moverGrupo, posicionCategoria, posicionGrupo } from './reordenamiento';

function categoria(id: number, grupoId: number, orden: number, oculta = false): CategoriaResponse {
  return {
    id,
    grupoId,
    nombre: `C${id}`,
    orden,
    oculta,
    nota: null,
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
  };
}

function grupo(
  id: number,
  orden: number,
  categorias: CategoriaResponse[],
  oculto = false,
): GrupoCategoriaConCategoriasResponse {
  return { id, nombre: `G${id}`, orden, oculto, categorias };
}

const nombres = (arbol: ArbolCategorias, grupoId: number) =>
  arbol.find((g) => g.id === grupoId)?.categorias.map((c) => c.nombre);

/**
 * Reproduce lo que hace el backend sobre la lista COMPLETA (ocultas incluidas): quitar la
 * categoría y meterla en `posicion`. Devuelve los nombres de las visibles del destino.
 */
function backend(
  completo: CategoriaResponse[],
  id: number,
  posicion: number,
  destino = completo,
): string[] {
  const enDestino = destino.filter((c) => c.id !== id);
  const movida = completo.find((c) => c.id === id) ?? destino.find((c) => c.id === id);
  enDestino.splice(posicion, 0, movida as CategoriaResponse);
  return enDestino.filter((c) => !c.oculta).map((c) => c.nombre);
}

describe('posicionGrupo', () => {
  const grupos = [grupo(1, 0, []), grupo(2, 1, []), grupo(3, 2, [])];

  it.each([
    [0, 2, 2],
    [2, 0, 0],
    [1, 2, 2],
    [0, 1, 1],
  ])('de %d a %d da la posición %d', (desde, hasta, esperada) => {
    expect(posicionGrupo(grupos, desde, hasta)).toBe(esperada);
  });

  it('el mismo lugar da null', () => {
    expect(posicionGrupo(grupos, 1, 1)).toBeNull();
  });

  it('con un grupo oculto en medio usa el orden del backend', () => {
    // Visibles con orden 0, 2 y 3: el grupo oculto de orden 1 no llega al frontend.
    const visibles = [grupo(1, 0, []), grupo(3, 2, []), grupo(4, 3, [])];

    expect(posicionGrupo(visibles, 0, 2)).toBe(3);
    expect(posicionGrupo(visibles, 2, 0)).toBe(0);
  });
});

describe('posicionCategoria dentro del mismo grupo', () => {
  const arbol = [grupo(1, 0, [categoria(1, 1, 0), categoria(2, 1, 1), categoria(3, 1, 2)])];

  it('hacia abajo y hacia arriba sin ocultas', () => {
    expect(posicionCategoria(arbol, 1, 1, 2)).toBe(2);
    expect(posicionCategoria(arbol, 3, 1, 0)).toBe(0);
    expect(posicionCategoria(arbol, 2, 1, 0)).toBe(0);
  });

  it('el mismo lugar da null', () => {
    expect(posicionCategoria(arbol, 2, 1, 1)).toBeNull();
  });

  describe('con una oculta en medio: A(0) H(1, oculta) B(2) C(3)', () => {
    const completo = [
      { ...categoria(1, 1, 0), nombre: 'A' },
      { ...categoria(9, 1, 1, true), nombre: 'H' },
      { ...categoria(2, 1, 2), nombre: 'B' },
      { ...categoria(3, 1, 3), nombre: 'C' },
    ];
    const visible = [
      grupo(
        1,
        0,
        completo.filter((c) => !c.oculta),
      ),
    ];

    it.each([
      ['A al final', 1, 2, 3, ['B', 'C', 'A']],
      ['C al principio', 3, 0, 0, ['C', 'A', 'B']],
      ['A al medio', 1, 1, 2, ['B', 'A', 'C']],
      ['C al medio', 3, 1, 2, ['A', 'C', 'B']],
    ])(
      '%s envía la posición %s y el backend deja lo mismo que la vista',
      (_caso, id, indice, posicionEsperada, ordenEsperado) => {
        const posicion = posicionCategoria(visible, id as number, 1, indice as number);

        expect(posicion).toBe(posicionEsperada);
        expect(backend(completo, id as number, posicion as number)).toEqual(ordenEsperado);
        expect(nombres(moverCategoria(visible, id as number, 1, indice as number), 1)).toEqual(
          ordenEsperado,
        );
      },
    );
  });
});

describe('posicionCategoria a otro grupo', () => {
  const origen = grupo(1, 0, [categoria(1, 1, 0), categoria(2, 1, 1)]);
  const destino = grupo(2, 1, [categoria(3, 2, 0), categoria(4, 2, 1)]);
  const vacio = grupo(3, 2, []);
  const arbol = [origen, destino, vacio];

  it('delante de la primera usa su orden', () => {
    expect(posicionCategoria(arbol, 1, 2, 0)).toBe(0);
  });

  it('entre dos usa el orden de la segunda', () => {
    expect(posicionCategoria(arbol, 1, 2, 1)).toBe(1);
  });

  it('al final usa el orden de la última más uno, que es m', () => {
    expect(posicionCategoria(arbol, 1, 2, 2)).toBe(2);
  });

  it('en un grupo vacío da 0', () => {
    expect(posicionCategoria(arbol, 1, 3, 0)).toBe(0);
  });

  it('con una oculta al final del destino, al final queda tras la última visible', () => {
    // Destino completo: D(0) E(1) H(2, oculta); visibles D y E.
    const completoDestino = [
      { ...categoria(3, 2, 0), nombre: 'D' },
      { ...categoria(4, 2, 1), nombre: 'E' },
      { ...categoria(9, 2, 2, true), nombre: 'H' },
    ];
    const conOculta = [
      origen,
      grupo(
        2,
        1,
        completoDestino.filter((c) => !c.oculta),
      ),
    ];

    const posicion = posicionCategoria(conOculta, 1, 2, 2);

    expect(posicion).toBe(2);
    expect(backend(origen.categorias, 1, posicion as number, completoDestino)).toEqual([
      'D',
      'E',
      'C1',
    ]);
  });

  it('una categoría que no está en el árbol da null', () => {
    expect(posicionCategoria(arbol, 99, 2, 0)).toBeNull();
  });
});

describe('moverGrupo y moverCategoria', () => {
  const arbol = [
    grupo(1, 0, [categoria(1, 1, 0), categoria(2, 1, 1), categoria(3, 1, 2)]),
    grupo(2, 1, [categoria(4, 2, 0)]),
    grupo(3, 2, []),
  ];
  const copia = JSON.stringify(arbol);

  it('moverGrupo reordena los grupos sin mutar el árbol', () => {
    const nuevo = moverGrupo(arbol, 0, 2);

    expect(nuevo.map((g) => g.id)).toEqual([2, 3, 1]);
    expect(JSON.stringify(arbol)).toBe(copia);
  });

  it('moverCategoria dentro del grupo', () => {
    expect(nombres(moverCategoria(arbol, 1, 1, 2), 1)).toEqual(['C2', 'C3', 'C1']);
    expect(JSON.stringify(arbol)).toBe(copia);
  });

  it('moverCategoria a otro grupo actualiza el grupoId', () => {
    const nuevo = moverCategoria(arbol, 2, 2, 1);

    expect(nombres(nuevo, 1)).toEqual(['C1', 'C3']);
    expect(nombres(nuevo, 2)).toEqual(['C4', 'C2']);
    expect(nuevo[1].categorias[1].grupoId).toBe(2);
    expect(JSON.stringify(arbol)).toBe(copia);
  });

  it('moverCategoria a un grupo vacío', () => {
    const nuevo = moverCategoria(arbol, 3, 3, 0);

    expect(nombres(nuevo, 3)).toEqual(['C3']);
    expect(nombres(nuevo, 1)).toEqual(['C1', 'C2']);
  });

  it('una categoría o un grupo inexistente devuelve el mismo árbol', () => {
    expect(moverCategoria(arbol, 99, 2, 0)).toBe(arbol);
    expect(moverCategoria(arbol, 1, 99, 0)).toBe(arbol);
  });
});
