import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ConciliacionResponse } from '../models/conciliacion-response.model';
import { CrearConciliacionRequest } from '../models/crear-conciliacion-request.model';
import { EstadoConciliacionResponse } from '../models/estado-conciliacion-response.model';

/** Acceso a `/presupuestos/{id}/cuentas/{cuentaId}/conciliacion`. */
@Injectable({ providedIn: 'root' })
export class ConciliacionService {
  private readonly http = inject(HttpClient);

  /** Diferencia con el extracto y las no conciliadas; `fecha` en `yyyy-MM-dd`. */
  estado(
    presupuestoId: number,
    cuentaId: number,
    saldoExtracto: number,
    fecha: string,
  ): Observable<EstadoConciliacionResponse> {
    const params = new HttpParams().set('saldoExtracto', saldoExtracto).set('fecha', fecha);
    return this.http.get<EstadoConciliacionResponse>(`${this.url(presupuestoId, cuentaId)}/estado`, {
      params,
    });
  }

  /** Cierra la conciliación (crea el ajuste si se pide y reconcilia hasta la fecha). */
  crear(
    presupuestoId: number,
    cuentaId: number,
    request: CrearConciliacionRequest,
  ): Observable<ConciliacionResponse> {
    return this.http.post<ConciliacionResponse>(this.url(presupuestoId, cuentaId), request);
  }

  /** Historial de la cuenta, de la más reciente a la más antigua. */
  historial(presupuestoId: number, cuentaId: number): Observable<ConciliacionResponse[]> {
    return this.http.get<ConciliacionResponse[]>(this.url(presupuestoId, cuentaId));
  }

  private url(presupuestoId: number, cuentaId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/cuentas/${cuentaId}/conciliacion`;
  }
}
