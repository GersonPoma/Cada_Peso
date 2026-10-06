import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarPresupuestoRequest } from '../models/actualizar-presupuesto-request.model';
import { CrearPresupuestoRequest } from '../models/crear-presupuesto-request.model';
import { PresupuestoResponse } from '../models/presupuesto-response.model';

/** Presupuestos de la persona autenticada. */
@Injectable({ providedIn: 'root' })
export class PresupuestoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${urlBaseApi}/presupuestos`;

  /** Todos los presupuestos, ordenados por nombre por el backend. */
  listar(): Observable<PresupuestoResponse[]> {
    return this.http.get<PresupuestoResponse[]>(this.url);
  }

  crear(solicitud: CrearPresupuestoRequest): Observable<PresupuestoResponse> {
    return this.http.post<PresupuestoResponse>(this.url, solicitud);
  }

  renombrar(id: number, solicitud: ActualizarPresupuestoRequest): Observable<PresupuestoResponse> {
    return this.http.put<PresupuestoResponse>(`${this.url}/${id}`, solicitud);
  }
}
