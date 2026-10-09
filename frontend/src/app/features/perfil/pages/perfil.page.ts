import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { leerProblemaApi } from '../../../shared/api/problema-api';
import { DatosPerfilComponent } from '../components/datos-perfil.component';
import { FormularioContrasenaComponent } from '../components/formulario-contrasena.component';
import { FormularioNombreComponent } from '../components/formulario-nombre.component';
import { PerfilResponse } from '../models/perfil-response.model';
import { PerfilService } from '../services/perfil.service';

type Estado = 'cargando' | 'listo' | 'error';

/**
 * Perfil de la persona autenticada (`/presupuestos/:presupuestoId/perfil`): sus datos, el cambio de
 * nombre y el cambio de contraseña, apilados en una sola columna. La cabecera, con `Cerrar sesión`,
 * la pone el layout del presupuesto. Cambiar la contraseña cierra la sesión local.
 */
@Component({
  selector: 'app-perfil',
  imports: [
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatProgressSpinner,
    DatosPerfilComponent,
    FormularioContrasenaComponent,
    FormularioNombreComponent,
  ],
  templateUrl: './perfil.page.html',
  styleUrl: './perfil.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerfilPage {
  private readonly servicio = inject(PerfilService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly estado = signal<Estado>('cargando');
  protected readonly perfil = signal<PerfilResponse | null>(null);

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.servicio
      .obtener()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (perfil) => {
          this.perfil.set(perfil);
          this.estado.set('listo');
        },
        error: (error: unknown) => {
          // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
          if (leerProblemaApi(error)?.status === 401) {
            return;
          }
          this.estado.set('error');
        },
      });
  }

  protected alGuardarNombre(actualizado: PerfilResponse): void {
    this.perfil.set(actualizado);
  }
}
