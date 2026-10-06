import { Routes } from '@angular/router';
import { invitadoGuard } from './core/auth/invitado.guard';
import { sesionGuard } from './core/auth/sesion.guard';
import { SeccionPresupuesto } from './features/presupuestos/models/seccion-presupuesto.model';

/** `data` de una sección del presupuesto: el layout arma su menú lateral con ella. */
const seccion = (etiqueta: string, icono: string): { seccion: SeccionPresupuesto } => ({
  seccion: { etiqueta, icono },
});

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [sesionGuard],
    loadComponent: () =>
      import('./features/presupuestos/pages/redireccion-presupuesto.page').then(
        (m) => m.RedireccionPresupuestoPage,
      ),
  },
  {
    path: 'presupuestos/:presupuestoId',
    canActivate: [sesionGuard],
    loadComponent: () =>
      import('./features/presupuestos/pages/layout-presupuesto.page').then(
        (m) => m.LayoutPresupuestoPage,
      ),
    // Cada feature agrega aquí su sección: una ruta hija con `data: seccion(...)`.
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/inicio/pages/inicio.page').then((m) => m.InicioPage),
        data: seccion('Inicio', 'home'),
      },
      {
        path: 'cuentas',
        loadComponent: () =>
          import('./features/cuentas/pages/cuentas.page').then((m) => m.CuentasPage),
        data: seccion('Cuentas', 'account_balance'),
      },
    ],
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
