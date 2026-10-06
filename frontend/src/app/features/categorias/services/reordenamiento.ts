import { ArbolCategorias } from '../models/arbol-categorias.model';
import { CategoriaResponse } from '../models/categoria-response.model';

/*
 * Reordenamiento del árbol de categorías, en funciones puras (sin Angular ni DOM).
 *
 * Los índices que reciben son los de las listas *visibles*, con la semántica del CDK de
 * arrastrar y soltar: dentro de la misma lista, `indiceDestino` es el índice final del elemento
 * (como `moveItemInArray`); en otra lista, es el índice donde se inserta (de 0 a su longitud).
 *
 * El backend, en cambio, numera las posiciones contando también los elementos ocultos (su campo
 * `orden`). Por eso la posición que se envía se toma del `orden` del elemento visible que ocupa
 * el lugar de destino: si el destino está debajo del origen, al quitar el elemento el destino
 * baja un lugar y la inserción en su `orden` lo deja justo detrás; si está arriba, justo delante.
 */

/** Posición del backend para mover un grupo, o `null` si no cambia de lugar. */
export function posicionGrupo(
  grupos: ArbolCategorias,
  desde: number,
  hasta: number,
): number | null {
  if (desde === hasta || !grupos[hasta]) {
    return null;
  }
  return grupos[hasta].orden;
}

/**
 * Posición del backend para mover una categoría al grupo `grupoDestinoId`, en el índice visible
 * `indiceDestino`, o `null` si no cambia de lugar o la categoría no está en el árbol.
 */
export function posicionCategoria(
  arbol: ArbolCategorias,
  categoriaId: number,
  grupoDestinoId: number,
  indiceDestino: number,
): number | null {
  const origen = ubicar(arbol, categoriaId);
  const destino = arbol.find((grupo) => grupo.id === grupoDestinoId);
  if (!origen || !destino) {
    return null;
  }
  const visibles = destino.categorias;
  if (origen.grupo.id === grupoDestinoId) {
    if (indiceDestino === origen.indice || !visibles[indiceDestino]) {
      return null;
    }
    return visibles[indiceDestino].orden;
  }
  if (indiceDestino < visibles.length) {
    return visibles[Math.max(indiceDestino, 0)].orden;
  }
  return visibles.length > 0 ? visibles[visibles.length - 1].orden + 1 : 0;
}

/** Árbol nuevo con el grupo del índice `desde` llevado al índice `hasta`; no muta el recibido. */
export function moverGrupo(arbol: ArbolCategorias, desde: number, hasta: number): ArbolCategorias {
  const grupos = [...arbol];
  const [movido] = grupos.splice(desde, 1);
  if (movido) {
    grupos.splice(hasta, 0, movido);
  }
  return grupos;
}

/**
 * Árbol nuevo con la categoría llevada al grupo `grupoDestinoId` en el índice visible
 * `indiceDestino` (con su `grupoId` actualizado); no muta el recibido.
 */
export function moverCategoria(
  arbol: ArbolCategorias,
  categoriaId: number,
  grupoDestinoId: number,
  indiceDestino: number,
): ArbolCategorias {
  const origen = ubicar(arbol, categoriaId);
  if (!origen || !arbol.some((grupo) => grupo.id === grupoDestinoId)) {
    return arbol;
  }
  const movida: CategoriaResponse = { ...origen.categoria, grupoId: grupoDestinoId };
  return arbol.map((grupo) => {
    let categorias = grupo.categorias;
    if (grupo.id === origen.grupo.id) {
      categorias = categorias.filter((categoria) => categoria.id !== categoriaId);
    }
    if (grupo.id === grupoDestinoId) {
      categorias = [...categorias];
      categorias.splice(indiceDestino, 0, movida);
    }
    return categorias === grupo.categorias ? grupo : { ...grupo, categorias };
  });
}

function ubicar(arbol: ArbolCategorias, categoriaId: number) {
  for (const grupo of arbol) {
    const indice = grupo.categorias.findIndex((categoria) => categoria.id === categoriaId);
    if (indice >= 0) {
      return { grupo, indice, categoria: grupo.categorias[indice] };
    }
  }
  return null;
}
