import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { CabeceraComponent } from '../../../shared/cabecera/cabecera.component';
import { DialogoPresupuestoComponent } from '../components/dialogo-presupuesto.component';
import { DatosDialogoPresupuesto } from '../models/datos-dialogo-presupuesto.model';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { PresupuestoService } from '../services/presupuesto.service';

type Estado = 'cargando' | 'sin-presupuestos' | 'error';

/**
 * Entrada de la parte privada (`/`): lleva al primer presupuesto de la persona o, si no tiene
 * ninguno, le ofrece crear el primero.
 */
@Component({
  selector: 'app-redireccion-presupuesto',
  imports: [MatButton, MatProgressSpinner, CabeceraComponent],
  templateUrl: './redireccion-presupuesto.page.html',
  styleUrl: './redireccion-presupuesto.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RedireccionPresupuestoPage {
  private readonly presupuestos = inject(PresupuestoService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly estado = signal<Estado>('cargando');

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.presupuestos
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          if (lista.length > 0) {
            this.irA(lista[0]);
          } else {
            this.estado.set('sin-presupuestos');
          }
        },
        error: (error: unknown) => {
          // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
          if (leerProblemaApi(error)?.status === 401) {
            return;
          }
          this.estado.set('error');
          this.snackBar
            .open(MENSAJE_ERROR_GENERICO, 'Reintentar', { duration: 6000 })
            .onAction()
            .subscribe(() => this.cargar());
        },
      });
  }

  protected crearPrimero(): void {
    this.dialog
      .open<DialogoPresupuestoComponent, DatosDialogoPresupuesto, PresupuestoResponse>(
        DialogoPresupuestoComponent,
        { data: { modo: 'crear' }, width: '400px' },
      )
      .afterClosed()
      .subscribe((presupuesto) => {
        if (presupuesto) {
          this.irA(presupuesto);
        }
      });
  }

  protected cerrarSesion(): void {
    this.sesion.cerrar();
    void this.router.navigateByUrl('/login');
  }

  private irA(presupuesto: PresupuestoResponse): void {
    void this.router.navigate(['/presupuestos', presupuesto.id], { replaceUrl: true });
  }
}
