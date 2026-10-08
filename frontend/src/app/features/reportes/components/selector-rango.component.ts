import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatOption } from '@angular/material/core';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';
import { mesActual } from '../../../shared/fecha/mes';
import { regionUsuario } from '../../../shared/formato/region-usuario';
import { RangoMeses, TipoAtajo } from '../models/rango-meses.model';
import { MENSAJE_RANGO_EXCEDIDO, MENSAJE_RANGO_INVERTIDO } from '../services/mensajes-reporte';
import { ATAJOS, atajo, textoRango, validarRango } from '../services/rango-reporte';

/** Primer y último año que admite el backend. */
const ANIO_MINIMO = 2000;
const ANIO_MAXIMO = 2100;

/** Lo que eligió la persona en los cuatro selectores, válido o no. */
interface Borrador {
  mesDesde: number;
  anioDesde: number;
  mesHasta: number;
  anioHasta: number;
}

/**
 * Selector del rango de meses compartido por los reportes: mes y año de `Desde` y de `Hasta`
 * (sin calendario de días) y atajos. Solo emite rangos válidos; uno inválido se explica y no se
 * emite, así el reporte sigue con el último rango válido.
 */
@Component({
  selector: 'app-selector-rango',
  imports: [MatButton, MatFormField, MatLabel, MatOption, MatSelect],
  templateUrl: './selector-rango.component.html',
  styleUrl: './selector-rango.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectorRangoComponent {
  readonly rango = input.required<RangoMeses>();
  /** Mes local de "hoy" para los atajos. */
  readonly mesLocal = input<string>(mesActual());
  readonly rangoCambiado = output<RangoMeses>();

  protected readonly atajos = ATAJOS;
  protected readonly anios = Array.from(
    { length: ANIO_MAXIMO - ANIO_MINIMO + 1 },
    (_, i) => ANIO_MINIMO + i,
  );
  protected readonly meses = Array.from({ length: 12 }, (_, i) => ({
    numero: i + 1,
    nombre: new Intl.DateTimeFormat(regionUsuario(), { month: 'long' }).format(
      new Date(2000, i, 1),
    ),
  }));

  protected readonly borrador = linkedSignal<RangoMeses, Borrador>({
    source: this.rango,
    computation: (rango) => {
      const [anioDesde, mesDesde] = rango.desde.split('-').map(Number);
      const [anioHasta, mesHasta] = rango.hasta.split('-').map(Number);
      return { mesDesde, anioDesde, mesHasta, anioHasta };
    },
  });

  private readonly rangoBorrador = computed<RangoMeses>(() => {
    const b = this.borrador();
    return { desde: mes(b.anioDesde, b.mesDesde), hasta: mes(b.anioHasta, b.mesHasta) };
  });

  protected readonly error = computed(() => {
    const rango = this.rangoBorrador();
    switch (validarRango(rango.desde, rango.hasta)) {
      case 'invertido':
        return MENSAJE_RANGO_INVERTIDO;
      case 'excede-maximo':
        return MENSAJE_RANGO_EXCEDIDO;
      default:
        return null;
    }
  });

  protected readonly texto = computed(() => textoRango(this.rango()));

  protected cambiar(campo: keyof Borrador, valor: number): void {
    this.borrador.update((b) => ({ ...b, [campo]: valor }));
    const rango = this.rangoBorrador();
    if (validarRango(rango.desde, rango.hasta) === null) {
      this.rangoCambiado.emit(rango);
    }
  }

  protected elegirAtajo(tipo: TipoAtajo): void {
    const rango = atajo(tipo, this.mesLocal());
    const [anioDesde, mesDesde] = rango.desde.split('-').map(Number);
    const [anioHasta, mesHasta] = rango.hasta.split('-').map(Number);
    this.borrador.set({ mesDesde, anioDesde, mesHasta, anioHasta });
    this.rangoCambiado.emit(rango);
  }
}

function mes(anio: number, numero: number): string {
  return `${String(anio).padStart(4, '0')}-${String(numero).padStart(2, '0')}`;
}
