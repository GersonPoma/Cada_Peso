import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';

/**
 * Lectura del árbol de categorías para esta feature (solo GET). Duplica a propósito una pequeña
 * parte de la feature de categorías: una feature no importa de otra.
 */
@Injectable({ providedIn: 'root' })
export class CategoriaLecturaService {
  private readonly http = inject(HttpClient);

  /** El árbol completo, con los grupos y las categorías ocultos. */
  arbol(presupuestoId: number): Observable<GrupoCategoriasLectura[]> {
    const params = new HttpParams().set('incluirOcultas', true);
    return this.http.get<GrupoCategoriasLectura[]>(
      `${urlBaseApi}/presupuestos/${presupuestoId}/categorias`,
      { params },
    );
  }
}
