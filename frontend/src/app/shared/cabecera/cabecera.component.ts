import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';

/** Barra superior con el nombre de la aplicación; el contenido proyectado va a la derecha. */
@Component({
  selector: 'app-cabecera',
  imports: [MatToolbar],
  templateUrl: './cabecera.component.html',
  styleUrl: './cabecera.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CabeceraComponent {}
