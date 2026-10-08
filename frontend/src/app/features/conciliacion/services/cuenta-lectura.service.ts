import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CuentaConciliacion } from '../models/cuenta-conciliacion.model';

/**
 * Lectura de una cuenta para esta feature (solo GET). Duplica a propósito una pequeña parte de la
 * feature de cuentas: una feature no importa de otra.
 */
@Injectable({ providedIn: 'root' })
export class CuentaLecturaService {
  private readonly http = inject(HttpClient);

  /** La cuenta, abierta o cerrada; `404` si no existe en el presupuesto. */
  obtener(presupuestoId: number, cuentaId: number): Observable<CuentaConciliacion> {
    return this.http.get<CuentaConciliacion>(
      `${urlBaseApi}/presupuestos/${presupuestoId}/cuentas/${cuentaId}`,
    );
  }
}
