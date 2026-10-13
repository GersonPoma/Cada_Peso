import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CuentaImportacion } from '../models/cuenta-importacion.model';

/**
 * Lectura de una cuenta para esta feature (solo GET). Duplica a propósito una pequeña parte de la
 * feature de cuentas: una feature no importa de otra.
 */
@Injectable({ providedIn: 'root' })
export class CuentaLecturaService {
  private readonly http = inject(HttpClient);

  /** La cuenta, abierta o cerrada; `404` si no existe en el presupuesto. */
  obtener(presupuestoId: number, cuentaId: number): Observable<CuentaImportacion> {
    return this.http.get<CuentaImportacion>(
      `${urlBaseApi}/presupuestos/${presupuestoId}/cuentas/${cuentaId}`,
    );
  }
}
