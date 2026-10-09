import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CambiarContrasenaRequest } from '../models/cambiar-contrasena-request.model';
import { CambiarNombreRequest } from '../models/cambiar-nombre-request.model';
import { PerfilResponse } from '../models/perfil-response.model';

/** Perfil de la persona autenticada: consulta, cambio de nombre y cambio de contraseña. */
@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);

  obtener(): Observable<PerfilResponse> {
    return this.http.get<PerfilResponse>(`${urlBaseApi}/usuarios/yo`);
  }

  cambiarNombre(solicitud: CambiarNombreRequest): Observable<PerfilResponse> {
    return this.http.put<PerfilResponse>(`${urlBaseApi}/usuarios/yo`, solicitud);
  }

  /** Responde `204` sin cuerpo. No se guarda ni se registra nada de la solicitud. */
  cambiarContrasena(solicitud: CambiarContrasenaRequest): Observable<void> {
    return this.http.post<void>(`${urlBaseApi}/usuarios/yo/contrasena`, solicitud);
  }
}
