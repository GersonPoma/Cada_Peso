import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Textos internos de `mat-paginator` en español. */
@Injectable()
export class PaginadorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Elementos por página:';
  override firstPageLabel = 'Primera página';
  override previousPageLabel = 'Página anterior';
  override nextPageLabel = 'Página siguiente';
  override lastPageLabel = 'Última página';

  override getRangeLabel = (pagina: number, tamanioPagina: number, total: number): string => {
    if (total === 0 || tamanioPagina === 0) {
      return `0 de ${total}`;
    }
    const inicio = pagina * tamanioPagina;
    const fin = Math.min(inicio + tamanioPagina, total);
    return `${inicio + 1} – ${fin} de ${total}`;
  };
}
