import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionService } from '../sesion/sesion.service';

/** Deja pasar solo con sesión; sin ella lleva a `/login`. */
export const sesionGuard: CanActivateFn = () => {
  return inject(SesionService).haySesion() ? true : inject(Router).createUrlTree(['/login']);
};
