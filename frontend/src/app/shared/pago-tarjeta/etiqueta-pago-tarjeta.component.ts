import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { ETIQUETA_PAGO_TARJETA } from './mensajes-pago-tarjeta';

/**
 * Marca una categoría o un grupo de pago de tarjeta: ícono y texto, para que el estado no dependa
 * solo del color. Solo presenta; el texto se lee con lectores de pantalla y el ícono se oculta.
 */
@Component({
  selector: 'app-etiqueta-pago-tarjeta',
  imports: [MatIcon],
  templateUrl: './etiqueta-pago-tarjeta.component.html',
  styleUrl: './etiqueta-pago-tarjeta.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EtiquetaPagoTarjetaComponent {
  protected readonly texto = ETIQUETA_PAGO_TARJETA;
}
