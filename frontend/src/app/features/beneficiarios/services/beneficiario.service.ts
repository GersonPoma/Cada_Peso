import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarBeneficiarioRequest } from '../models/actualizar-beneficiario-request.model';
import { BeneficiarioResponse } from '../models/beneficiario-response.model';
import { CrearBeneficiarioRequest } from '../models/crear-beneficiario-request.model';

/** Beneficiarios de un presupuesto. Cada método recibe el id del presupuesto. */
@Injectable({ providedIn: 'root' })
export class BeneficiarioService {
  private readonly http = inject(HttpClient);

  /** Todos los beneficiarios, ordenados por nombre por el backend. */
  listar(presupuestoId: number): Observable<BeneficiarioResponse[]> {
    return this.http.get<BeneficiarioResponse[]>(this.url(presupuestoId));
  }

  crear(
    presupuestoId: number,
    solicitud: CrearBeneficiarioRequest,
  ): Observable<BeneficiarioResponse> {
    return this.http.post<BeneficiarioResponse>(this.url(presupuestoId), solicitud);
  }

  actualizar(
    presupuestoId: number,
    id: number,
    solicitud: ActualizarBeneficiarioRequest,
  ): Observable<BeneficiarioResponse> {
    return this.http.put<BeneficiarioResponse>(`${this.url(presupuestoId)}/${id}`, solicitud);
  }

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/beneficiarios`;
  }
}
