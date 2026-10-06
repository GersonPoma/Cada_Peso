import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { UsuarioActualResponse } from '../models/usuario-actual-response.model';
import { UsuarioActualService } from '../services/usuario-actual.service';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar tus datos.';

/**
 * Sección de inicio de un presupuesto: saluda al usuario autenticado. La cabecera y el cierre de
 * sesión los pone el layout del presupuesto.
 */
@Component({
  selector: 'app-inicio',
  imports: [MatProgressSpinner],
  templateUrl: './inicio.page.html',
  styleUrl: './inicio.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioPage {
  private readonly usuarioActual = inject(UsuarioActualService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly usuario = signal<UsuarioActualResponse | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);

  constructor() {
    this.usuarioActual
      .obtener()
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe({
        next: (usuario) => {
          this.usuario.set(usuario);
          this.cargando.set(false);
        },
        error: (error: unknown) => {
          this.cargando.set(false);
          // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
          if (leerProblemaApi(error)?.status === 401) {
            return;
          }
          this.errorCarga.set(MENSAJE_ERROR_CARGA);
          this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
        },
      });
  }
}
