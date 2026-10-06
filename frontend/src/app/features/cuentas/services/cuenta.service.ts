import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarCuentaRequest } from '../models/actualizar-cuenta-request.model';
import { CrearCuentaRequest } from '../models/crear-cuenta-request.model';
import { CuentaResponse } from '../models/cuenta-response.model';
import { SaldoCuentaResponse } from '../models/saldo-cuenta-response.model';

/** Cuentas de un presupuesto y sus saldos. Cada método recibe el id del presupuesto. */
@Injectable({ providedIn: 'root' })
export class CuentaService {
  private readonly http = inject(HttpClient);

  /** Cuentas ordenadas por nombre por el backend; las cerradas solo si se piden. */
  listar(presupuestoId: number, incluirCerradas: boolean): Observable<CuentaResponse[]> {
    const params = new HttpParams().set('incluirCerradas', incluirCerradas);
    return this.http.get<CuentaResponse[]>(this.urlCuentas(presupuestoId), { params });
  }

  crear(presupuestoId: number, solicitud: CrearCuentaRequest): Observable<CuentaResponse> {
    return this.http.post<CuentaResponse>(this.urlCuentas(presupuestoId), solicitud);
  }

  actualizar(
    presupuestoId: number,
    id: number,
    solicitud: ActualizarCuentaRequest,
  ): Observable<CuentaResponse> {
    return this.http.put<CuentaResponse>(`${this.urlCuentas(presupuestoId)}/${id}`, solicitud);
  }

  cerrar(presupuestoId: number, id: number): Observable<CuentaResponse> {
    return this.http.post<CuentaResponse>(`${this.urlCuentas(presupuestoId)}/${id}/cerrar`, null);
  }

  reabrir(presupuestoId: number, id: number): Observable<CuentaResponse> {
    return this.http.post<CuentaResponse>(`${this.urlCuentas(presupuestoId)}/${id}/reabrir`, null);
  }

  /** Saldo de cada cuenta del presupuesto, abiertas y cerradas. */
  saldos(presupuestoId: number): Observable<SaldoCuentaResponse[]> {
    return this.http.get<SaldoCuentaResponse[]>(
      `${urlBaseApi}/presupuestos/${presupuestoId}/transacciones/saldos`,
    );
  }

  private urlCuentas(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/cuentas`;
  }
}
