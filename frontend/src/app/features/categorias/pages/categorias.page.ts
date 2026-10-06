import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { ComponentType } from '@angular/cdk/portal';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, Observable, catchError, filter, switchMap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { DialogoCategoriaComponent } from '../components/dialogo-categoria.component';
import { DialogoGrupoComponent } from '../components/dialogo-grupo.component';
import {
  DialogoMoverCategoriaComponent,
  MENSAJE_CATEGORIA_REPETIDA_DESTINO,
} from '../components/dialogo-mover-categoria.component';
import {
  DatoArrastre,
  GrupoCategoriasComponent,
  SoltarCategoria,
} from '../components/grupo-categorias.component';
import { ArbolCategorias } from '../models/arbol-categorias.model';
import { CategoriaResponse } from '../models/categoria-response.model';
import {
  DatosDialogoCategoria,
  DatosDialogoGrupo,
  DatosDialogoMoverCategoria,
} from '../models/datos-dialogos-categorias.model';
import { GrupoCategoriaConCategoriasResponse } from '../models/grupo-categoria-con-categorias-response.model';
import { CategoriaService } from '../services/categoria.service';
import {
  moverCategoria,
  moverGrupo,
  posicionCategoria,
  posicionGrupo,
} from '../services/reordenamiento';

type Estado = 'cargando' | 'listo' | 'error';

/**
 * Árbol de grupos de categorías y categorías del presupuesto activo. Permite crearlos,
 * renombrarlos o editarlos, ocultarlos y mostrarlos, y reordenarlos arrastrando (con
 * actualización optimista y reversión si el backend lo rechaza) o con "Mover a...".
 */
@Component({
  selector: 'app-categorias',
  imports: [
    CdkDrag,
    CdkDropList,
    CdkDropListGroup,
    MatButton,
    MatIcon,
    MatProgressSpinner,
    MatSlideToggle,
    GrupoCategoriasComponent,
  ],
  templateUrl: './categorias.page.html',
  styleUrl: './categorias.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoriasPage {
  private readonly servicio = inject(CategoriaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly estado = signal<Estado>('cargando');
  protected readonly arbol = signal<ArbolCategorias | null>(null);
  protected readonly incluirOcultas = signal(false);
  /** Hay un `/mover` en curso (o la recarga que lo sigue): se ignoran los arrastres nuevos. */
  protected readonly moviendo = signal(false);
  private readonly recargas = signal(0);

  /** La lista de grupos solo acepta grupos, nunca una categoría. */
  protected readonly soloGrupos = (arrastre: CdkDrag<DatoArrastre>): boolean =>
    arrastre.data?.tipo === 'grupo';

  constructor() {
    const peticion = computed(() => ({
      presupuestoId: this.presupuestoActivo.presupuesto()?.id ?? null,
      incluirOcultas: this.incluirOcultas(),
      recargas: this.recargas(),
    }));
    toObservable(peticion)
      .pipe(
        filter(({ presupuestoId }) => presupuestoId !== null),
        // switchMap descarta una respuesta vieja si cambia el interruptor o se recarga.
        switchMap(({ presupuestoId, incluirOcultas }) =>
          this.servicio.obtenerArbol(presupuestoId as number, incluirOcultas).pipe(
            catchError((error: unknown) => {
              this.alFallarCarga(error);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((arbol) => {
        this.arbol.set(arbol);
        this.estado.set('listo');
        this.moviendo.set(false);
      });
  }

  protected recargar(): void {
    this.recargas.update((valor) => valor + 1);
  }

  protected cambiarOcultas(incluir: boolean): void {
    this.incluirOcultas.set(incluir);
  }

  protected agregarGrupo(): void {
    this.abrir<DatosDialogoGrupo>(DialogoGrupoComponent, { modo: 'crear' });
  }

  protected renombrarGrupo(grupo: GrupoCategoriaConCategoriasResponse): void {
    this.abrir<DatosDialogoGrupo>(DialogoGrupoComponent, {
      modo: 'renombrar',
      grupo: { id: grupo.id, nombre: grupo.nombre },
    });
  }

  protected agregarCategoria(grupo: GrupoCategoriaConCategoriasResponse): void {
    this.abrir<DatosDialogoCategoria>(DialogoCategoriaComponent, {
      modo: 'crear',
      grupoId: grupo.id,
    });
  }

  protected editarCategoria(categoria: CategoriaResponse): void {
    this.abrir<DatosDialogoCategoria>(DialogoCategoriaComponent, { modo: 'editar', categoria });
  }

  protected moverCategoriaA(categoria: CategoriaResponse): void {
    this.abrir<DatosDialogoMoverCategoria>(DialogoMoverCategoriaComponent, {
      arbol: this.arbol() ?? [],
      categoria,
    });
  }

  protected alternarVisibilidadGrupo(grupo: GrupoCategoriaConCategoriasResponse): void {
    this.ejecutar((presupuestoId) =>
      grupo.oculto
        ? this.servicio.mostrarGrupo(presupuestoId, grupo.id)
        : this.servicio.ocultarGrupo(presupuestoId, grupo.id),
    );
  }

  protected alternarVisibilidadCategoria(categoria: CategoriaResponse): void {
    this.ejecutar((presupuestoId) =>
      categoria.oculta
        ? this.servicio.mostrarCategoria(presupuestoId, categoria.id)
        : this.servicio.ocultarCategoria(presupuestoId, categoria.id),
    );
  }

  protected soltarGrupo(evento: CdkDragDrop<ArbolCategorias, ArbolCategorias, DatoArrastre>): void {
    const arbol = this.arbol();
    if (this.moviendo() || !arbol || evento.item.data?.tipo !== 'grupo') {
      return;
    }
    const posicion = posicionGrupo(arbol, evento.previousIndex, evento.currentIndex);
    if (posicion === null) {
      return;
    }
    const grupoId = evento.item.data.grupo.id;
    this.moverOptimista(moverGrupo(arbol, evento.previousIndex, evento.currentIndex), (id) =>
      this.servicio.moverGrupo(id, grupoId, { posicion }),
    );
  }

  protected soltarCategoria(evento: SoltarCategoria): void {
    const arbol = this.arbol();
    if (this.moviendo() || !arbol || evento.item.data?.tipo !== 'categoria') {
      return;
    }
    const categoriaId = evento.item.data.categoria.id;
    const grupoId = evento.container.data.id;
    const posicion = posicionCategoria(arbol, categoriaId, grupoId, evento.currentIndex);
    if (posicion === null) {
      return;
    }
    this.moverOptimista(moverCategoria(arbol, categoriaId, grupoId, evento.currentIndex), (id) =>
      this.servicio.moverCategoria(id, categoriaId, { grupoId, posicion }),
    );
  }

  /**
   * Muestra `nuevoArbol` de inmediato y envía el movimiento. Si sale bien, recarga para tener los
   * `orden` al día; si falla, vuelve al árbol anterior, avisa y recarga. `moviendo` sigue activo
   * hasta que llega la recarga.
   */
  private moverOptimista(
    nuevoArbol: ArbolCategorias,
    mover: (presupuestoId: number) => Observable<unknown>,
  ): void {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id;
    const anterior = this.arbol();
    if (presupuestoId === undefined || !anterior) {
      return;
    }
    this.moviendo.set(true);
    this.arbol.set(nuevoArbol);
    mover(presupuestoId).subscribe({
      next: () => this.recargar(),
      error: (error: unknown) => {
        this.arbol.set(anterior);
        const problema = leerProblemaApi(error);
        if (problema?.status === 401) {
          this.moviendo.set(false);
          return;
        }
        const mensaje =
          problema?.codigo === CODIGOS_API.CATEGORIA_YA_EXISTE
            ? MENSAJE_CATEGORIA_REPETIDA_DESTINO
            : MENSAJE_ERROR_GENERICO;
        this.snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
        this.recargar();
      },
    });
  }

  private ejecutar(operacion: (presupuestoId: number) => Observable<unknown>): void {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id;
    if (presupuestoId === undefined) {
      return;
    }
    operacion(presupuestoId).subscribe({
      next: () => this.recargar(),
      error: (error: unknown) => {
        if (leerProblemaApi(error)?.status !== 401) {
          this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
        }
      },
    });
  }

  /** Abre un diálogo de la feature y recarga el árbol si se cerró con un resultado. */
  private abrir<D>(componente: ComponentType<unknown>, datos: D): void {
    this.dialog
      .open<unknown, D, unknown>(componente, { data: datos, width: '440px' })
      .afterClosed()
      .subscribe((resultado: unknown) => {
        if (resultado) {
          this.recargar();
        }
      });
  }

  private alFallarCarga(error: unknown): void {
    this.moviendo.set(false);
    // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
    if (leerProblemaApi(error)?.status === 401) {
      return;
    }
    if (this.arbol() === null) {
      this.estado.set('error');
    }
    this.snackBar
      .open(MENSAJE_ERROR_GENERICO, 'Reintentar', { duration: 6000 })
      .onAction()
      .subscribe(() => this.recargar());
  }
}
