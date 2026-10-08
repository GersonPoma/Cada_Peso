import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { GrupoMesResponse } from '../models/grupo-mes-response.model';
import { MetaMesResponse } from '../models/metas-mes-response.model';
import { CeldaAsignadoComponent } from './celda-asignado.component';
import { IndicadorMetaComponent } from './indicador-meta.component';

/** Pedido de asignar un monto (milésimas) a una categoría. */
export interface Asignacion {
  categoriaId: number;
  asignado: number;
}

let siguienteId = 0;

/**
 * Un grupo del mes: encabezado plegable con la suma de sus categorías y una fila por categoría
 * (asignado editable, actividad y disponible con su indicador de sobregasto) y, si tiene meta, su
 * indicador debajo del nombre. Solo presenta y emite: la página hace las peticiones.
 */
@Component({
  selector: 'app-grupo-mes',
  imports: [
    MatIcon,
    MatIconButton,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
    MontoPipe,
    CeldaAsignadoComponent,
    IndicadorMetaComponent,
  ],
  templateUrl: './grupo-mes.component.html',
  styleUrl: './grupo-mes.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GrupoMesComponent {
  readonly grupo = input.required<GrupoMesResponse>();
  readonly moneda = input.required<string>();
  /** Categorías con un guardado de asignado en curso. */
  readonly guardando = input<ReadonlySet<number>>(new Set());
  /** Mensaje de error del backend por categoría. */
  readonly errores = input<ReadonlyMap<number, string>>(new Map());
  /** Meta del mes por categoría; las categorías sin meta no están. */
  readonly metas = input<ReadonlyMap<number, MetaMesResponse>>(new Map());
  /** Categorías con un posponer o reanudar en curso: sus acciones de meta se deshabilitan. */
  readonly metasEnCurso = input<ReadonlySet<number>>(new Set());
  /** El mes mostrado es anterior al actual (el indicador usa un tono neutro). */
  readonly mesPasado = input(false);

  readonly asignar = output<Asignacion>();
  readonly moverDinero = output<CategoriaMesResponse>();
  readonly cubrirSobregasto = output<CategoriaMesResponse>();
  /** Agregar o editar la meta (según si la categoría está en `metas`). */
  readonly editarMeta = output<CategoriaMesResponse>();
  readonly posponerMeta = output<CategoriaMesResponse>();
  readonly reanudarMeta = output<CategoriaMesResponse>();

  protected readonly idLista = `categorias-grupo-${siguienteId++}`;
  protected readonly desplegado = signal(true);

  /** Suma de las categorías del grupo, calculada en el frontend (milésimas). */
  protected readonly totales = computed(() =>
    this.grupo().categorias.reduce(
      (suma, categoria) => ({
        asignado: suma.asignado + categoria.asignado,
        actividad: suma.actividad + categoria.actividad,
        disponible: suma.disponible + categoria.disponible,
      }),
      { asignado: 0, actividad: 0, disponible: 0 },
    ),
  );

  protected alternar(): void {
    this.desplegado.update((valor) => !valor);
  }
}
