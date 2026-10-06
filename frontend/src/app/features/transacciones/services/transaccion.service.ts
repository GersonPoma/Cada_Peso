import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarTransaccionRequest } from '../models/actualizar-transaccion-request.model';
import { CrearTransaccionRequest } from '../models/crear-transaccion-request.model';
import { EstadoTransaccion } from '../models/estado-transaccion.model';
import { FiltrosTransacciones } from '../models/filtros-transacciones.model';
import { LoteRequest } from '../models/lote-request.model';
import { LoteResponse } from '../models/lote-response.model';
import { PaginaTransacciones } from '../models/pagina-transacciones.model';
import { SaldoCuenta } from '../models/saldo-cuenta.model';
import { TransaccionResponse } from '../models/transaccion-response.model';

/** Transacciones de un presupuesto. Cada método recibe el id del presupuesto. */
@Injectable({ providedIn: 'root' })
export class TransaccionService {
  private readonly http = inject(HttpClient);

  /** Página de transacciones con los filtros con sus nombres de la API. */
  listar(presupuestoId: number, filtros: FiltrosTransacciones): Observable<PaginaTransacciones> {
    let params = new HttpParams().set('page', filtros.pagina).set('size', filtros.tamano);
    const opcionales: [string, string | number | null][] = [
      ['cuentaId', filtros.cuentaId],
      ['categoriaId', filtros.categoriaId],
      ['desde', filtros.desde],
      ['hasta', filtros.hasta],
      ['estado', filtros.estado],
      ['q', filtros.q],
    ];
    for (const [nombre, valor] of opcionales) {
      if (valor !== null && valor !== '') {
        params = params.set(nombre, valor);
      }
    }
    if (filtros.soloSinAprobar) {
      params = params.set('soloSinAprobar', true);
    }
    return this.http.get<PaginaTransacciones>(this.url(presupuestoId), { params });
  }

  obtener(presupuestoId: number, id: number): Observable<TransaccionResponse> {
    return this.http.get<TransaccionResponse>(`${this.url(presupuestoId)}/${id}`);
  }

  crear(
    presupuestoId: number,
    solicitud: CrearTransaccionRequest,
  ): Observable<TransaccionResponse> {
    return this.http.post<TransaccionResponse>(this.url(presupuestoId), solicitud);
  }

  actualizar(
    presupuestoId: number,
    id: number,
    solicitud: ActualizarTransaccionRequest,
  ): Observable<TransaccionResponse> {
    return this.http.put<TransaccionResponse>(`${this.url(presupuestoId)}/${id}`, solicitud);
  }

  borrar(presupuestoId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.url(presupuestoId)}/${id}`);
  }

  aprobar(presupuestoId: number, id: number): Observable<TransaccionResponse> {
    return this.http.post<TransaccionResponse>(`${this.url(presupuestoId)}/${id}/aprobar`, null);
  }

  cambiarEstado(
    presupuestoId: number,
    id: number,
    estado: EstadoTransaccion,
  ): Observable<TransaccionResponse> {
    return this.http.put<TransaccionResponse>(`${this.url(presupuestoId)}/${id}/estado`, {
      estado,
    });
  }

  moverCuenta(
    presupuestoId: number,
    id: number,
    cuentaId: number,
  ): Observable<TransaccionResponse> {
    return this.http.post<TransaccionResponse>(`${this.url(presupuestoId)}/${id}/mover-cuenta`, {
      cuentaId,
    });
  }

  duplicar(presupuestoId: number, id: number): Observable<TransaccionResponse> {
    return this.http.post<TransaccionResponse>(`${this.url(presupuestoId)}/${id}/duplicar`, null);
  }

  lote(presupuestoId: number, solicitud: LoteRequest): Observable<LoteResponse> {
    return this.http.post<LoteResponse>(`${this.url(presupuestoId)}/lote`, solicitud);
  }

  saldos(presupuestoId: number): Observable<SaldoCuenta[]> {
    return this.http.get<SaldoCuenta[]>(`${this.url(presupuestoId)}/saldos`);
  }

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/transacciones`;
  }
}
