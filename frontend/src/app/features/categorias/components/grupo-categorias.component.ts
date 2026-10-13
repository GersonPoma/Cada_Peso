import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { EtiquetaPagoTarjetaComponent } from '../../../shared/pago-tarjeta/etiqueta-pago-tarjeta.component';
import { NOTA_CATEGORIA_PAGO_TARJETA } from '../../../shared/pago-tarjeta/mensajes-pago-tarjeta';
import { CategoriaResponse } from '../models/categoria-response.model';
import { GrupoCategoriaConCategoriasResponse } from '../models/grupo-categoria-con-categorias-response.model';
import { esCategoriaDePago, esGrupoDePagos } from '../services/pago-tarjeta';

/** Lo que lleva cada elemento arrastrable: un grupo o una categoría. */
export type DatoArrastre =
  | { tipo: 'grupo'; grupo: GrupoCategoriaConCategoriasResponse }
  | { tipo: 'categoria'; categoria: CategoriaResponse };

/** Evento al soltar una categoría en la lista de un grupo. */
export type SoltarCategoria = CdkDragDrop<
  GrupoCategoriaConCategoriasResponse,
  GrupoCategoriaConCategoriasResponse,
  DatoArrastre
>;

/**
 * Un grupo de categorías: cabecera con su asa de arrastre, nombre, "Agregar categoría" y menú, y
 * la lista de categorías, que recibe categorías arrastradas desde cualquier grupo. Solo presenta:
 * emite cada acción y la página decide qué hacer. El grupo de pagos de tarjetas y sus categorías
 * están protegidos (el backend rechaza tocarlos): se muestran con su etiqueta y sin acciones.
 */
@Component({
  selector: 'app-grupo-categorias',
  imports: [
    CdkDrag,
    CdkDragHandle,
    CdkDropList,
    EtiquetaPagoTarjetaComponent,
    MatButton,
    MatIcon,
    MatIconButton,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
  ],
  templateUrl: './grupo-categorias.component.html',
  styleUrl: './grupo-categorias.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GrupoCategoriasComponent {
  readonly grupo = input.required<GrupoCategoriaConCategoriasResponse>();
  /** Con un movimiento en curso no se puede arrastrar. */
  readonly moviendo = input(false);

  readonly renombrar = output<void>();
  readonly alternarVisibilidad = output<void>();
  readonly agregarCategoria = output<void>();
  readonly editarCategoria = output<CategoriaResponse>();
  readonly alternarVisibilidadCategoria = output<CategoriaResponse>();
  readonly moverCategoriaA = output<CategoriaResponse>();
  readonly soltarCategoria = output<SoltarCategoria>();

  /** El grupo de pagos de tarjetas: no se renombra, oculta, mueve ni recibe categorías. */
  protected readonly esDePagos = computed(() => esGrupoDePagos(this.grupo()));
  protected readonly notaPago = NOTA_CATEGORIA_PAGO_TARJETA;

  /** La lista solo acepta categorías, nunca un grupo, y el grupo de pagos no acepta ninguna. */
  protected readonly soloCategorias = (arrastre: CdkDrag<DatoArrastre>): boolean =>
    arrastre.data?.tipo === 'categoria' && !this.esDePagos();

  protected esPago(categoria: CategoriaResponse): boolean {
    return esCategoriaDePago(categoria);
  }

  protected datoCategoria(categoria: CategoriaResponse): DatoArrastre {
    return { tipo: 'categoria', categoria };
  }
}
