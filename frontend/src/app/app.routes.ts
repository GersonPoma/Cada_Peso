import { Routes } from '@angular/router';
import { invitadoGuard } from './core/auth/invitado.guard';
import { sesionGuard } from './core/auth/sesion.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [sesionGuard],
    loadComponent: () => import('./features/inicio/pages/inicio.page').then((m) => m.InicioPage),
  },
  {
    path: 'login',
    canActivate: [invitadoGuard],
    loadComponent: () => import('./features/auth/pages/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'registro',
    canActivate: [invitadoGuard],
    loadComponent: () => import('./features/auth/pages/registro.page').then((m) => m.RegistroPage),
  },
  { path: '**', redirectTo: '' },
];
