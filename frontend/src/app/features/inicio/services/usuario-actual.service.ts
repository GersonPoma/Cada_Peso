import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { UsuarioActualResponse } from '../models/usuario-actual-response.model';

/** Datos del usuario autenticado. */
@Injectable({ providedIn: 'root' })
export class UsuarioActualService {
  private readonly http = inject(HttpClient);

  obtener(): Observable<UsuarioActualResponse> {
    return this.http.get<UsuarioActualResponse>(`${urlBaseApi}/usuarios/yo`);
  }
}
