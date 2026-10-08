import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { BeneficiarioSugerido } from '../models/beneficiario-sugerido.model';

/**
 * Lectura de los beneficiarios del presupuesto para esta feature (solo GET). Duplica a propósito
 * una pequeña parte de la feature de beneficiarios: una feature no importa de otra.
 */
@Injectable({ providedIn: 'root' })
export class BeneficiarioLecturaService {
  private readonly http = inject(HttpClient);

  /** Los beneficiarios cuyo nombre empieza por `q` (el backend ignora mayúsculas). */
  buscar(presupuestoId: number, q: string, limite = 10): Observable<BeneficiarioSugerido[]> {
    const params = new HttpParams().set('q', q).set('limite', limite);
    return this.http.get<BeneficiarioSugerido[]>(this.url(presupuestoId), { params });
  }

  /** Todos los beneficiarios, ordenados por nombre. */
  listar(presupuestoId: number): Observable<BeneficiarioSugerido[]> {
    return this.http.get<BeneficiarioSugerido[]>(this.url(presupuestoId));
  }

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/beneficiarios`;
  }
}
