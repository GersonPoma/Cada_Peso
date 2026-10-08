import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarTransferenciaRequest } from '../models/actualizar-transferencia-request.model';
import { CrearTransferenciaRequest } from '../models/crear-transferencia-request.model';
import { TransferenciaResponse } from '../models/transferencia-response.model';

/**
 * Transferencias entre cuentas: dos transacciones enlazadas que se crean, editan y borran juntas.
 * `transaccionId` es el id de cualquiera de las dos patas.
 */
@Injectable({ providedIn: 'root' })
export class TransferenciaService {
  private readonly http = inject(HttpClient);

  crear(
    presupuestoId: number,
    solicitud: CrearTransferenciaRequest,
  ): Observable<TransferenciaResponse> {
    return this.http.post<TransferenciaResponse>(this.url(presupuestoId), solicitud);
  }

  obtener(presupuestoId: number, transaccionId: number): Observable<TransferenciaResponse> {
    return this.http.get<TransferenciaResponse>(`${this.url(presupuestoId)}/${transaccionId}`);
  }

  actualizar(
    presupuestoId: number,
    transaccionId: number,
    solicitud: ActualizarTransferenciaRequest,
  ): Observable<TransferenciaResponse> {
    return this.http.put<TransferenciaResponse>(
      `${this.url(presupuestoId)}/${transaccionId}`,
      solicitud,
    );
  }

  borrar(presupuestoId: number, transaccionId: number): Observable<void> {
    return this.http.delete<void>(`${this.url(presupuestoId)}/${transaccionId}`);
  }

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/transferencias`;
  }
}
