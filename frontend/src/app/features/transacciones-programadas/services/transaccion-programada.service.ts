import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarProgramadaRequest } from '../models/actualizar-programada-request.model';
import { CrearProgramadaRequest } from '../models/crear-programada-request.model';
import { GeneracionResponse } from '../models/generacion-response.model';
import { TransaccionProgramadaResponse } from '../models/transaccion-programada-response.model';

/** Acceso a `/presupuestos/{id}/transacciones-programadas`. */
@Injectable({ providedIn: 'root' })
export class TransaccionProgramadaService {
  private readonly http = inject(HttpClient);

  /** Todas las plantillas (activas, pausadas y finalizadas), en el orden de la API. */
  listar(presupuestoId: number): Observable<TransaccionProgramadaResponse[]> {
    return this.http.get<TransaccionProgramadaResponse[]>(this.url(presupuestoId));
  }

  crear(
    presupuestoId: number,
    request: CrearProgramadaRequest,
  ): Observable<TransaccionProgramadaResponse> {
    return this.http.post<TransaccionProgramadaResponse>(this.url(presupuestoId), request);
  }

  actualizar(
    presupuestoId: number,
    id: number,
    request: ActualizarProgramadaRequest,
  ): Observable<TransaccionProgramadaResponse> {
    return this.http.put<TransaccionProgramadaResponse>(
      `${this.url(presupuestoId)}/${id}`,
      request,
    );
  }

  /** Las transacciones que ya generó se conservan (sin vínculo con la plantilla). */
  borrar(presupuestoId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.url(presupuestoId)}/${id}`);
  }

  pausar(presupuestoId: number, id: number): Observable<TransaccionProgramadaResponse> {
    return this.http.post<TransaccionProgramadaResponse>(
      `${this.url(presupuestoId)}/${id}/pausar`,
      null,
    );
  }

  /** No genera las ocurrencias del período pausado. */
  reanudar(presupuestoId: number, id: number): Observable<TransaccionProgramadaResponse> {
    return this.http.post<TransaccionProgramadaResponse>(
      `${this.url(presupuestoId)}/${id}/reanudar`,
      null,
    );
  }

  /** Genera las ocurrencias vencidas de este presupuesto; es idempotente. */
  generar(presupuestoId: number): Observable<GeneracionResponse> {
    return this.http.post<GeneracionResponse>(`${this.url(presupuestoId)}/generar`, null);
  }

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/transacciones-programadas`;
  }
}
