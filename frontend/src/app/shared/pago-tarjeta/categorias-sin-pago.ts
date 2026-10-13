/** Lo mínimo que se lee de una categoría: su id y si es la de pago de una tarjeta. */
interface CategoriaConPago {
  id: number;
  esPagoTarjeta?: boolean;
}

/**
 * Quita de los grupos las categorías de pago de tarjeta, que el backend no admite en una
 * transacción (`422`), y los grupos que quedan sin categorías. `conservarId` deja una categoría
 * aunque sea de pago (la que ya está elegida en un filtro). Función pura: no muta la entrada.
 * Es genérica por estructura para que `shared/` no dependa de los modelos de ninguna feature.
 */
export function sinCategoriasDePago<
  C extends CategoriaConPago,
  G extends { categorias: readonly C[] },
>(grupos: readonly G[], conservarId: number | null = null): G[] {
  return grupos
    .map((grupo) => ({
      ...grupo,
      categorias: grupo.categorias.filter((c) => c.esPagoTarjeta !== true || c.id === conservarId),
    }))
    .filter((grupo) => grupo.categorias.length > 0);
}
