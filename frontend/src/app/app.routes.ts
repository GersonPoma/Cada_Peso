import { Routes } from '@angular/router';
import { invitadoGuard } from './core/auth/invitado.guard';
import { sesionGuard } from './core/auth/sesion.guard';
import { mesActual } from './features/presupuesto-mensual/services/mes';
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
      // La sección por defecto es el presupuesto del mes actual. Va directo al mes: el router no
      // encadena dos redirecciones relativas en el mismo nivel.
      { path: '', pathMatch: 'full', redirectTo: () => `presupuesto/${mesActual()}` },
      {
        path: 'presupuesto',
        pathMatch: 'full',
        // Se evalúa al entrar: siempre el mes local de ese momento.
        redirectTo: () => `presupuesto/${mesActual()}`,
        data: seccion('Presupuesto', 'account_balance_wallet'),
      },
      {
        path: 'presupuesto/:mes',
        loadComponent: () =>
          import('./features/presupuesto-mensual/pages/presupuesto-mensual.page').then(
            (m) => m.PresupuestoMensualPage,
          ),
      },
      {
        path: 'inicio',
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
      {
        // Sin `seccion`: se llega desde Cuentas y Transacciones, no desde el menú lateral.
        path: 'cuentas/:cuentaId/conciliacion',
        loadComponent: () =>
          import('./features/conciliacion/pages/conciliacion.page').then((m) => m.ConciliacionPage),
      },
      {
        path: 'transacciones',
        loadComponent: () =>
          import('./features/transacciones/pages/transacciones.page').then(
            (m) => m.TransaccionesPage,
          ),
        data: seccion('Transacciones', 'receipt_long'),
      },
      {
        path: 'categorias',
        loadComponent: () =>
          import('./features/categorias/pages/categorias.page').then((m) => m.CategoriasPage),
        data: seccion('Categorías', 'category'),
      },
      {
        path: 'beneficiarios',
        loadComponent: () =>
          import('./features/beneficiarios/pages/beneficiarios.page').then(
            (m) => m.BeneficiariosPage,
          ),
        data: seccion('Beneficiarios', 'storefront'),
      },
      {
        path: 'reportes',
        loadComponent: () =>
          import('./features/reportes/pages/reportes.page').then((m) => m.ReportesPage),
        data: seccion('Reportes', 'bar_chart'),
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
