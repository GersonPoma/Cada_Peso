import { Pipe, PipeTransform } from '@angular/core';
import { regionUsuario } from './region-usuario';

@Pipe({
  name: 'monto',
})
export class MontoPipe implements PipeTransform {
  transform(valorMilesimas: number | null | undefined, codigoMoneda: string): string {
    if (valorMilesimas === null || valorMilesimas === undefined) {
      return '';
    }

    const formateador = new Intl.NumberFormat(regionUsuario(), {
      style: 'currency',
      currency: codigoMoneda,
    });

    return formateador.format(valorMilesimas / 1000);
  }
}
