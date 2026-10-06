import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { AsignacionActualizadaResponse } from '../models/asignacion-actualizada-response.model';
import { AsignarRequest } from '../models/asignar-request.model';
import { MesPresupuestoResponse } from '../models/mes-presupuesto-response.model';
import { MoverDineroRequest } from '../models/mover-dinero-request.model';

/** El presupuesto de un mes (`yyyy-MM`): consultarlo, asignar y mover dinero entre categorías. */
@Injectable({ providedIn: 'root' })
export class MesPresupuestoService {
  private readonly http = inject(HttpClient);

  obtener(
    presupuestoId: number,
    mes: string,
    incluirOcultas: boolean,
  ): Observable<MesPresupuestoResponse> {
    const params = new HttpParams().set('incluirOcultas', incluirOcultas);
    return this.http.get<MesPresupuestoResponse>(this.url(presupuestoId, mes), { params });
  }

  /** Fija (no suma) el asignado de la categoría en el mes. */
  asignar(
    presupuestoId: number,
    mes: string,
    categoriaId: number,
    solicitud: AsignarRequest,
  ): Observable<AsignacionActualizadaResponse> {
    return this.http.put<AsignacionActualizadaResponse>(
      `${this.url(presupuestoId, mes)}/categorias/${categoriaId}`,
      solicitud,
    );
  }

  /** Mueve dinero entre dos categorías; responde el mes completo actualizado. */
  moverDinero(
    presupuestoId: number,
    mes: string,
    solicitud: MoverDineroRequest,
  ): Observable<MesPresupuestoResponse> {
    return this.http.post<MesPresupuestoResponse>(
      `${this.url(presupuestoId, mes)}/mover-dinero`,
      solicitud,
    );
  }

  private url(presupuestoId: number, mes: string): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/meses/${mes}`;
  }
}
