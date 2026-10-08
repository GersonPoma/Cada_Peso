import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { AutoAsignarRequest, AutoAsignarResponse } from '../models/auto-asignar.model';
import { GuardarMetaRequest } from '../models/guardar-meta-request.model';
import { MetaResponse } from '../models/meta-response.model';
import { MetaMesResponse, MetasMesResponse } from '../models/metas-mes-response.model';

/**
 * Metas de las categorías: guardarlas, consultarlas y quitarlas; su estado en un mes (`yyyy-MM`),
 * posponerlas o reanudarlas en ese mes y auto-asignar el mes.
 */
@Injectable({ providedIn: 'root' })
export class MetaService {
  private readonly http = inject(HttpClient);

  /** Crea o reemplaza la meta de la categoría. */
  guardar(
    presupuestoId: number,
    categoriaId: number,
    solicitud: GuardarMetaRequest,
  ): Observable<MetaResponse> {
    return this.http.put<MetaResponse>(this.urlMeta(presupuestoId, categoriaId), solicitud);
  }

  obtener(presupuestoId: number, categoriaId: number): Observable<MetaResponse> {
    return this.http.get<MetaResponse>(this.urlMeta(presupuestoId, categoriaId));
  }

  quitar(presupuestoId: number, categoriaId: number): Observable<void> {
    return this.http.delete<void>(this.urlMeta(presupuestoId, categoriaId));
  }

  /** Las metas del mes con sus cifras; `incluirOcultas` como en el mes. */
  delMes(
    presupuestoId: number,
    mes: string,
    incluirOcultas: boolean,
  ): Observable<MetasMesResponse> {
    const params = new HttpParams().set('incluirOcultas', incluirOcultas);
    return this.http.get<MetasMesResponse>(`${this.urlMes(presupuestoId, mes)}/metas`, { params });
  }

  posponer(presupuestoId: number, mes: string, categoriaId: number): Observable<MetaMesResponse> {
    return this.http.post<MetaMesResponse>(
      `${this.urlMes(presupuestoId, mes)}/metas/${categoriaId}/posponer`,
      null,
    );
  }

  reanudar(presupuestoId: number, mes: string, categoriaId: number): Observable<MetaMesResponse> {
    return this.http.post<MetaMesResponse>(
      `${this.urlMes(presupuestoId, mes)}/metas/${categoriaId}/reanudar`,
      null,
    );
  }

  /** Con `simular: true` solo calcula los cambios; con `false` los aplica. */
  autoAsignar(
    presupuestoId: number,
    mes: string,
    solicitud: AutoAsignarRequest,
  ): Observable<AutoAsignarResponse> {
    return this.http.post<AutoAsignarResponse>(
      `${this.urlMes(presupuestoId, mes)}/auto-asignar`,
      solicitud,
    );
  }

  private urlMeta(presupuestoId: number, categoriaId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/categorias/${categoriaId}/meta`;
  }

  private urlMes(presupuestoId: number, mes: string): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/meses/${mes}`;
  }
}
