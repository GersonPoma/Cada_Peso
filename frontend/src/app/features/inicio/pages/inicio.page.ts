import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { CabeceraComponent } from '../../../shared/cabecera/cabecera.component';
import { UsuarioActualResponse } from '../models/usuario-actual-response.model';
import { UsuarioActualService } from '../services/usuario-actual.service';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar tus datos.';

/** Página protegida de inicio: saluda al usuario autenticado y permite cerrar sesión. */
@Component({
  selector: 'app-inicio',
  imports: [MatButton, MatProgressSpinner, CabeceraComponent],
  templateUrl: './inicio.page.html',
  styleUrl: './inicio.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioPage {
  private readonly usuarioActual = inject(UsuarioActualService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
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

  protected cerrarSesion(): void {
    this.sesion.cerrar();
    void this.router.navigateByUrl('/login');
  }
}
