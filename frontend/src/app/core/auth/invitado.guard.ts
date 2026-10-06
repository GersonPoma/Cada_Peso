import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionService } from '../sesion/sesion.service';

/** Deja pasar solo sin sesión (pantallas de login y registro); con sesión lleva a `/`. */
export const invitadoGuard: CanActivateFn = () => {
  return inject(SesionService).haySesion() ? inject(Router).createUrlTree(['/']) : true;
};
