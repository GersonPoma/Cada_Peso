import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { urlBaseApi } from '../api-base-url';
import { SesionService } from '../sesion/sesion.service';

/**
 * Agrega `Authorization: Bearer <token>` a las peticiones a `/api/v1` cuando hay sesión. Ante un
 * 401 de cualquier petición a la API que no sea de `/api/v1/auth/` (inicio de sesión y registro,
 * donde un 401 significa credenciales incorrectas), borra la sesión y lleva a `/login`. Siempre
 * relanza el error para que quien hizo la petición lo vea.
 */
export const sesionInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  const esApi = peticion.url === urlBaseApi || peticion.url.startsWith(`${urlBaseApi}/`);
  const esAuth = peticion.url.startsWith(`${urlBaseApi}/auth/`);
  const token = sesion.token();
  const conToken =
    esApi && token
      ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : peticion;

  return siguiente(conToken).pipe(
    catchError((error: unknown) => {
      if (esApi && !esAuth && error instanceof HttpErrorResponse && error.status === 401) {
        sesion.cerrar();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
