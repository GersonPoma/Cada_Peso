import { describe, expect, it } from 'vitest';
import { sinCategoriasDePago } from './categorias-sin-pago';

interface Categoria {
  id: number;
  nombre: string;
  esPagoTarjeta?: boolean;
}

interface Grupo {
  id: number;
  nombre: string;
  categorias: Categoria[];
}

const ARBOL: Grupo[] = [
  {
    id: 1,
    nombre: 'Necesidades',
    categorias: [
      { id: 10, nombre: 'Comida', esPagoTarjeta: false },
      { id: 11, nombre: 'Luz' },
    ],
  },
  {
    id: 2,
    nombre: 'Pagos de tarjetas de crédito',
    categorias: [
      { id: 20, nombre: 'Pago: Visa', esPagoTarjeta: true },
      { id: 21, nombre: 'Pago: Master', esPagoTarjeta: true },
    ],
  },
];

describe('sinCategoriasDePago', () => {
  it('quita las categorías de pago y el grupo que se queda vacío', () => {
    const resultado = sinCategoriasDePago(ARBOL);

    expect(resultado.map((g) => g.nombre)).toEqual(['Necesidades']);
    expect(resultado[0].categorias.map((c) => c.nombre)).toEqual(['Comida', 'Luz']);
  });

  it('un grupo mezclado conserva solo sus categorías normales', () => {
    const mezclado: Grupo[] = [
      {
        id: 3,
        nombre: 'Mixto',
        categorias: [
          { id: 30, nombre: 'Ocio' },
          { id: 31, nombre: 'Pago: Visa', esPagoTarjeta: true },
        ],
      },
    ];

    expect(sinCategoriasDePago(mezclado)[0].categorias.map((c) => c.nombre)).toEqual(['Ocio']);
  });

  it('trata el campo ausente como categoría normal', () => {
    const sinCampo: Grupo[] = [{ id: 1, nombre: 'G', categorias: [{ id: 1, nombre: 'A' }] }];

    expect(sinCategoriasDePago(sinCampo)).toEqual(sinCampo);
  });

  it('conserva la categoría indicada aunque sea de pago, con su grupo', () => {
    const resultado = sinCategoriasDePago(ARBOL, 20);

    expect(resultado.map((g) => g.nombre)).toEqual(['Necesidades', 'Pagos de tarjetas de crédito']);
    expect(resultado[1].categorias.map((c) => c.id)).toEqual([20]);
  });

  it('un conservarId que no es de pago no cambia nada', () => {
    expect(sinCategoriasDePago(ARBOL, 10)).toEqual(sinCategoriasDePago(ARBOL));
  });

  it('no muta la entrada', () => {
    const copia = structuredClone(ARBOL);

    sinCategoriasDePago(ARBOL, 20);

    expect(ARBOL).toEqual(copia);
  });

  it('sin categorías de pago devuelve lo mismo', () => {
    expect(sinCategoriasDePago([ARBOL[0]])).toEqual([ARBOL[0]]);
  });

  it('sin grupos devuelve una lista vacía', () => {
    expect(sinCategoriasDePago([])).toEqual([]);
  });
});
