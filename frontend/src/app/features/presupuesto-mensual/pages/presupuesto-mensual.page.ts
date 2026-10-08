import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { BarraMesComponent } from '../components/barra-mes.component';
import { DialogoAutoAsignarComponent } from '../components/dialogo-auto-asignar.component';
import { DialogoMetaComponent } from '../components/dialogo-meta.component';
import { DialogoMoverDineroComponent } from '../components/dialogo-mover-dinero.component';
import { Asignacion, GrupoMesComponent } from '../components/grupo-mes.component';
import { ResumenListoParaAsignarComponent } from '../components/resumen-listo-para-asignar.component';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { DatosDialogoMoverDinero } from '../models/datos-dialogo-mover-dinero.model';
import {
  DatosDialogoAutoAsignar,
  DatosDialogoMeta,
  ResultadoDialogoAutoAsignar,
  ResultadoDialogoMeta,
} from '../models/datos-dialogos-metas.model';
import { MesPresupuestoResponse } from '../models/mes-presupuesto-response.model';
import { MetaMesResponse, MetasMesResponse } from '../models/metas-mes-response.model';
import { ResultadoMoverDinero } from '../models/resultado-mover-dinero.model';
import { MesPresupuestoService } from '../services/mes-presupuesto.service';
import { esMesValido, mesActual } from '../services/mes';
import { MetaService } from '../services/meta.service';

export const MENSAJE_META_QUITADA = 'La categoría ya no tiene meta';

/** Aviso tras aplicar una auto-asignación, con la cantidad real de categorías que cambiaron. */
export function mensajeAutoAsignado(cambios: number): string {
  return cambios === 1 ? 'Se actualizó 1 categoría' : `Se actualizaron ${cambios} categorías`;
}

type Estado = 'cargando' | 'listo' | 'error';

/**
 * Presupuesto mensual (base cero) del presupuesto activo. El mes manda desde la URL
 * (`presupuesto/:mes`). Permite asignar dinero a cada categoría (con actualización optimista),
 * ver actividad y disponible, el "Listo para asignar" y mover dinero entre categorías. Con el mes
 * pide sus metas (mismo `incluirOcultas`): muestra su estado en cada fila, permite agregarlas,
 * editarlas, quitarlas, posponerlas o reanudarlas en el mes y auto-asignar con vista previa.
 */
@Component({
  selector: 'app-presupuesto-mensual',
  imports: [
    RouterLink,
    MatButton,
    MatProgressSpinner,
    MatSlideToggle,
    MontoPipe,
    BarraMesComponent,
    GrupoMesComponent,
    ResumenListoParaAsignarComponent,
  ],
  templateUrl: './presupuesto-mensual.page.html',
  styleUrl: './presupuesto-mensual.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PresupuestoMensualPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servicio = inject(MesPresupuestoService);
  private readonly metaService = inject(MetaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** `:mes` de la URL, tal como llega (puede ser inválido). */
  protected readonly mes = toSignal(this.ruta.paramMap.pipe(map((p) => p.get('mes') ?? '')), {
    initialValue: '',
  });
  /** Solo un mes válido se muestra: uno inválido se reemplaza enseguida por el actual. */
  protected readonly mesValido = computed(() => esMesValido(this.mes()));
  protected readonly presupuestoId = computed(() => this.presupuestoActivo.presupuesto()?.id);
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? null);

  protected readonly estado = signal<Estado>('cargando');
  protected readonly datos = signal<MesPresupuestoResponse | null>(null);
  protected readonly incluirOcultas = signal(false);
  /** Categorías con un guardado de asignado en curso. */
  protected readonly guardando = signal<ReadonlySet<number>>(new Set());
  /** Mensaje del backend por categoría (un 400 al asignar). */
  protected readonly errores = signal<ReadonlyMap<number, string>>(new Map());
  private readonly recargas = signal(0);

  /** Metas del mes mostrado; `null` mientras carga o si fallaron. */
  protected readonly metas = signal<MetasMesResponse | null>(null);
  /** El mes cargó pero sus metas no. */
  protected readonly errorMetas = signal(false);
  /** Categorías con un posponer o reanudar en curso. */
  protected readonly metasEnCurso = signal<ReadonlySet<number>>(new Set());

  protected readonly metasPorCategoria = computed(
    () =>
      new Map<number, MetaMesResponse>(
        (this.metas()?.metas ?? []).map((meta) => [meta.categoriaId, meta]),
      ),
  );
  /** El mes mostrado es anterior al actual (local): sus faltantes se ven en tono neutro. */
  protected readonly mesPasado = computed(() => this.mesValido() && this.mes() < mesActual());

  /** Totales del mes sumando las categorías mostradas (siguen al día tras editar). */
  protected readonly totales = computed(() => {
    const suma = { asignado: 0, actividad: 0, disponible: 0 };
    for (const grupo of this.datos()?.grupos ?? []) {
      for (const categoria of grupo.categorias) {
        suma.asignado += categoria.asignado;
        suma.actividad += categoria.actividad;
        suma.disponible += categoria.disponible;
      }
    }
    return suma;
  });

  protected readonly sinCategorias = computed(() =>
    (this.datos()?.grupos ?? []).every((grupo) => grupo.categorias.length === 0),
  );

  constructor() {
    const peticion = computed(() => ({
      presupuestoId: this.presupuestoId(),
      mes: this.mes(),
      incluirOcultas: this.incluirOcultas(),
      recargas: this.recargas(),
    }));
    toObservable(peticion)
      .pipe(
        filter(({ presupuestoId, mes }) => {
          if (presupuestoId === undefined) {
            return false;
          }
          if (!esMesValido(mes)) {
            void this.irAMes(mesActual(), true);
            return false;
          }
          return true;
        }),
        tap(({ mes }) => {
          // Otro mes: no se muestran los datos del anterior mientras llega el nuevo.
          if (this.datos()?.mes !== mes) {
            this.datos.set(null);
            this.metas.set(null);
            this.estado.set('cargando');
          }
        }),
        // switchMap descarta la respuesta de un mes viejo si se cambia de mes rápido; el mes y
        // sus metas viajan juntos, así una respuesta atrasada se descarta entera.
        switchMap(({ presupuestoId, mes, incluirOcultas }) =>
          forkJoin([
            this.servicio.obtener(presupuestoId as number, mes, incluirOcultas),
            // Si solo fallan las metas, el mes sigue usable sin indicadores.
            this.metaService
              .delMes(presupuestoId as number, mes, incluirOcultas)
              .pipe(catchError(() => of(null))),
          ]).pipe(
            catchError((error: unknown) => {
              this.alFallarCarga(error);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(([mes, metas]) => {
        this.mostrar(mes);
        this.metas.set(metas);
        this.errorMetas.set(metas === null);
      });
  }

  protected irAMes(mes: string, reemplazar = false): Promise<boolean> {
    return this.router.navigate(['..', mes], { relativeTo: this.ruta, replaceUrl: reemplazar });
  }

  protected recargar(): void {
    this.recargas.update((valor) => valor + 1);
  }

  protected cambiarOcultas(incluir: boolean): void {
    this.incluirOcultas.set(incluir);
  }

  /**
   * Fija el asignado de una categoría: lo muestra de inmediato (con el disponible y el listo para
   * asignar ajustados por la diferencia), envía el PUT y aplica la respuesta; si falla, revierte
   * solo esa fila y esa diferencia. Una categoría guarda de a una vez.
   */
  protected asignar({ categoriaId, asignado }: Asignacion): void {
    const presupuestoId = this.presupuestoId();
    const mes = this.datos()?.mes;
    const anterior = this.buscar(categoriaId);
    if (presupuestoId === undefined || !mes || !anterior || this.guardando().has(categoriaId)) {
      return;
    }
    const diferencia = asignado - anterior.asignado;
    const disponible = anterior.disponible + diferencia;
    this.reemplazar(
      { ...anterior, asignado, disponible, sobregastada: disponible < 0 },
      -diferencia,
    );
    this.marcarGuardando(categoriaId, true);
    this.ponerError(categoriaId, null);

    this.servicio.asignar(presupuestoId, mes, categoriaId, { asignado }).subscribe({
      next: (respuesta) => {
        this.marcarGuardando(categoriaId, false);
        if (this.datos()?.mes !== mes) {
          return;
        }
        this.reemplazar(respuesta.categoria, 0);
        this.datos.update((d) => d && { ...d, listoParaAsignar: respuesta.listoParaAsignar });
      },
      error: (error: unknown) => {
        this.marcarGuardando(categoriaId, false);
        if (this.datos()?.mes === mes) {
          this.reemplazar(anterior, diferencia);
        }
        this.alFallarAsignacion(categoriaId, error);
      },
    });
  }

  protected moverDinero(categoria: CategoriaMesResponse): void {
    this.abrirMoverDinero({ origenId: categoria.categoriaId });
  }

  protected cubrirSobregasto(categoria: CategoriaMesResponse): void {
    this.abrirMoverDinero({
      destinoId: categoria.categoriaId,
      monto: Math.max(0, -categoria.disponible),
    });
  }

  /** Agregar o editar la meta; cualquier cierre con resultado vuelve a pedir el mes y sus metas. */
  protected editarMeta(categoria: CategoriaMesResponse): void {
    this.dialog
      .open<DialogoMetaComponent, DatosDialogoMeta, ResultadoDialogoMeta>(DialogoMetaComponent, {
        data: {
          categoriaId: categoria.categoriaId,
          nombre: categoria.nombre,
          tieneMeta: this.metasPorCategoria().has(categoria.categoriaId),
        },
        width: '480px',
      })
      .afterClosed()
      .subscribe((resultado) => {
        if (resultado) {
          this.recargar();
        }
      });
  }

  protected posponerMeta(categoria: CategoriaMesResponse): void {
    this.cambiarPausaMeta(categoria, 'posponer');
  }

  protected reanudarMeta(categoria: CategoriaMesResponse): void {
    this.cambiarPausaMeta(categoria, 'reanudar');
  }

  protected autoAsignar(): void {
    const mes = this.datos();
    if (!mes) {
      return;
    }
    const metas = this.metasPorCategoria();
    const categorias = mes.grupos.flatMap((grupo) =>
      grupo.categorias.map((c) => ({
        categoriaId: c.categoriaId,
        nombre: c.nombre,
        esPagoTarjeta: c.esPagoTarjeta,
        tieneMeta: metas.has(c.categoriaId),
      })),
    );
    this.dialog
      .open<DialogoAutoAsignarComponent, DatosDialogoAutoAsignar, ResultadoDialogoAutoAsignar>(
        DialogoAutoAsignarComponent,
        { data: { mes: mes.mes, categorias }, width: '520px' },
      )
      .afterClosed()
      .subscribe((resultado) => {
        if (resultado?.tipo === 'aplicado') {
          this.snackBar.open(mensajeAutoAsignado(resultado.cambios), 'Cerrar', {
            duration: 6000,
          });
        }
        if (resultado) {
          this.recargar();
        }
      });
  }

  /** Posponer o reanudar la meta en el mes; al terminar (bien o mal) vuelve a pedir el mes. */
  private cambiarPausaMeta(categoria: CategoriaMesResponse, accion: 'posponer' | 'reanudar') {
    const presupuestoId = this.presupuestoId();
    const mes = this.datos()?.mes;
    const { categoriaId } = categoria;
    if (presupuestoId === undefined || !mes || this.metasEnCurso().has(categoriaId)) {
      return;
    }
    this.marcarMetaEnCurso(categoriaId, true);
    this.metaService[accion](presupuestoId, mes, categoriaId).subscribe({
      next: () => {
        this.marcarMetaEnCurso(categoriaId, false);
        this.recargar();
      },
      error: (error: unknown) => {
        this.marcarMetaEnCurso(categoriaId, false);
        const problema = leerProblemaApi(error);
        if (problema?.status === 401) {
          return;
        }
        const mensaje =
          problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO
            ? MENSAJE_META_QUITADA
            : MENSAJE_ERROR_GENERICO;
        this.snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
        this.recargar();
      },
    });
  }

  private marcarMetaEnCurso(categoriaId: number, enCurso: boolean): void {
    this.metasEnCurso.update((actual) => {
      const nuevo = new Set(actual);
      if (enCurso) {
        nuevo.add(categoriaId);
      } else {
        nuevo.delete(categoriaId);
      }
      return nuevo;
    });
  }

  private abrirMoverDinero(preseleccion: Omit<DatosDialogoMoverDinero, 'mes'>): void {
    const mes = this.datos();
    if (!mes) {
      return;
    }
    this.dialog
      .open<DialogoMoverDineroComponent, DatosDialogoMoverDinero, ResultadoMoverDinero>(
        DialogoMoverDineroComponent,
        { data: { mes, ...preseleccion }, width: '440px' },
      )
      .afterClosed()
      .subscribe((resultado) => {
        if (resultado?.tipo === 'movido') {
          this.mostrar(this.sinOcultasSiCorresponde(resultado.mes));
        } else if (resultado?.tipo === 'recargar') {
          this.recargar();
        }
      });
  }

  private mostrar(mes: MesPresupuestoResponse): void {
    this.datos.set(mes);
    this.estado.set('listo');
    this.errores.set(new Map());
  }

  /** El backend devuelve el mes de mover dinero con las ocultas: se quitan si no se muestran. */
  private sinOcultasSiCorresponde(mes: MesPresupuestoResponse): MesPresupuestoResponse {
    if (this.incluirOcultas()) {
      return mes;
    }
    return {
      ...mes,
      grupos: mes.grupos
        .filter((grupo) => !grupo.oculto)
        .map((grupo) => ({ ...grupo, categorias: grupo.categorias.filter((c) => !c.oculta) })),
    };
  }

  private buscar(categoriaId: number): CategoriaMesResponse | undefined {
    for (const grupo of this.datos()?.grupos ?? []) {
      const encontrada = grupo.categorias.find((c) => c.categoriaId === categoriaId);
      if (encontrada) {
        return encontrada;
      }
    }
    return undefined;
  }

  /** Reemplaza la fila de la categoría y suma `deltaListo` al listo para asignar. */
  private reemplazar(categoria: CategoriaMesResponse, deltaListo: number): void {
    this.datos.update(
      (mes) =>
        mes && {
          ...mes,
          listoParaAsignar: mes.listoParaAsignar + deltaListo,
          grupos: mes.grupos.map((grupo) => ({
            ...grupo,
            categorias: grupo.categorias.map((c) =>
              c.categoriaId === categoria.categoriaId ? categoria : c,
            ),
          })),
        },
    );
  }

  private marcarGuardando(categoriaId: number, guardando: boolean): void {
    this.guardando.update((actual) => {
      const nuevo = new Set(actual);
      if (guardando) {
        nuevo.add(categoriaId);
      } else {
        nuevo.delete(categoriaId);
      }
      return nuevo;
    });
  }

  private ponerError(categoriaId: number, mensaje: string | null): void {
    this.errores.update((actual) => {
      const nuevo = new Map(actual);
      if (mensaje) {
        nuevo.set(categoriaId, mensaje);
      } else {
        nuevo.delete(categoriaId);
      }
      return nuevo;
    });
  }

  private alFallarAsignacion(categoriaId: number, error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    const mensajeCampo = problema?.errores?.['asignado'];
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && mensajeCampo) {
      this.ponerError(categoriaId, mensajeCampo);
      return;
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    if (
      problema?.codigo === CODIGOS_API.CONFLICTO ||
      problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO
    ) {
      this.recargar();
    }
  }

  private alFallarCarga(error: unknown): void {
    // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
    if (leerProblemaApi(error)?.status === 401) {
      return;
    }
    if (this.datos() === null) {
      this.estado.set('error');
    }
    this.snackBar
      .open(MENSAJE_ERROR_GENERICO, 'Reintentar', { duration: 6000 })
      .onAction()
      .subscribe(() => this.recargar());
  }
}
