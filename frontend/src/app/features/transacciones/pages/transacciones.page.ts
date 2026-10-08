import { ComponentType } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, filter, forkJoin, map, switchMap, tap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { BarraLoteComponent } from '../components/barra-lote.component';
import { DialogoConfirmacionComponent } from '../components/dialogo-confirmacion.component';
import { DialogoMoverCuentaComponent } from '../components/dialogo-mover-cuenta.component';
import { DialogoTransaccionComponent } from '../components/dialogo-transaccion.component';
import { DialogoTransferenciaComponent } from '../components/dialogo-transferencia.component';
import { FiltrosTransaccionesComponent } from '../components/filtros-transacciones.component';
import {
  AccionFila,
  TablaTransaccionesComponent,
} from '../components/tabla-transacciones.component';
import { BeneficiarioSugerido } from '../models/beneficiario-sugerido.model';
import { CuentaResumen } from '../models/cuenta-resumen.model';
import {
  DatosDialogoConfirmacion,
  DatosDialogoMoverCuenta,
  DatosDialogoTransaccion,
  DatosDialogoTransferencia,
  ResultadoDialogoTransaccion,
} from '../models/datos-dialogos-transacciones.model';
import { FiltrosTransacciones } from '../models/filtros-transacciones.model';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';
import { OperacionLote } from '../models/operacion-lote.model';
import { PaginaTransacciones } from '../models/pagina-transacciones.model';
import { SaldoCuenta } from '../models/saldo-cuenta.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { filtrarLote } from '../services/acciones-transaccion';
import { BeneficiarioLecturaService } from '../services/beneficiario-lectura.service';
import { CategoriaLecturaService } from '../services/categoria-lectura.service';
import { CuentaLecturaService } from '../services/cuenta-lectura.service';
import {
  TAMANOS_PAGINA,
  aQueryParams,
  filtrosVacios,
  hayFiltros,
  leerFiltros,
} from '../services/filtros-url';
import { TransaccionService } from '../services/transaccion.service';
import { TransferenciaService } from '../services/transferencia.service';

type Estado = 'cargando' | 'listo' | 'error';

/** Máximo de ids que acepta una operación en lote. */
export const MAXIMO_LOTE = 100;

export const MENSAJE_ACCION_NO_PERMITIDA =
  'No se puede: la transacción está reconciliada o es parte de una transferencia.';
export const MENSAJE_TRANSACCION_INEXISTENTE =
  'La transacción ya no existe. Actualizamos la lista.';
export const MENSAJE_LOTE_SIN_APLICABLES = 'Ninguna de las seleccionadas admite esta acción.';
export const MENSAJE_BORRAR_TRANSFERENCIA =
  'Se borrarán las dos transacciones de esta transferencia (la salida y la entrada). No se puede ' +
  'deshacer.';

const NOMBRE_OPERACION: Readonly<Record<OperacionLote, string>> = {
  APROBAR: 'aprobar',
  CATEGORIZAR: 'categorizar',
  BORRAR: 'borrar',
};

/**
 * Registro de transacciones del presupuesto activo. Los filtros y la página viven en la URL; la
 * lista se pide al servidor paginada. Permite crear, editar, dividir, aprobar, conciliar, mover,
 * duplicar, borrar y operar en lote, y muestra los saldos de las cuentas.
 */
@Component({
  selector: 'app-transacciones',
  imports: [
    MatButton,
    MatIcon,
    MatPaginator,
    MatProgressSpinner,
    MontoPipe,
    RouterLink,
    BarraLoteComponent,
    FiltrosTransaccionesComponent,
    TablaTransaccionesComponent,
  ],
  templateUrl: './transacciones.page.html',
  styleUrl: './transacciones.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransaccionesPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servicio = inject(TransaccionService);
  private readonly transferencias = inject(TransferenciaService);
  private readonly cuentasServicio = inject(CuentaLecturaService);
  private readonly categoriasServicio = inject(CategoriaLecturaService);
  private readonly beneficiariosServicio = inject(BeneficiarioLecturaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Filtros y página, leídos siempre de la URL. */
  protected readonly filtros = toSignal(this.ruta.queryParamMap.pipe(map(leerFiltros)), {
    initialValue: leerFiltros(this.ruta.snapshot.queryParamMap),
  });
  protected readonly presupuestoId = computed(() => this.presupuestoActivo.presupuesto()?.id);
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? null);
  protected readonly tamanos = TAMANOS_PAGINA;

  protected readonly estado = signal<Estado>('cargando');
  protected readonly pagina = signal<PaginaTransacciones | null>(null);
  protected readonly saldos = signal<SaldoCuenta[]>([]);
  protected readonly cuentas = signal<CuentaResumen[]>([]);
  protected readonly gruposTodos = signal<GrupoCategoriasResumen[]>([]);
  private readonly beneficiarios = signal<BeneficiarioSugerido[]>([]);
  protected readonly seleccion = signal<ReadonlySet<number>>(new Set());
  private readonly recargas = signal(0);
  private readonly recargasListas = signal(0);
  private readonly recargasBeneficiarios = signal(0);

  protected readonly nombresCuenta = computed(
    () => new Map(this.cuentas().map((c) => [c.id, c.nombre] as const)),
  );
  protected readonly cuentasCerradas = computed(
    () =>
      new Set(
        this.cuentas()
          .filter((c) => c.cerrada)
          .map((c) => c.id),
      ),
  );
  protected readonly nombresCategoria = computed(
    () =>
      new Map(
        this.gruposTodos().flatMap((g) => g.categorias.map((c) => [c.id, c.nombre] as const)),
      ),
  );
  /** Nombre actual de cada beneficiario, para la columna de la tabla. */
  protected readonly nombresBeneficiario = computed(
    () => new Map(this.beneficiarios().map((b) => [b.id, b.nombre] as const)),
  );
  /** Grupos y categorías visibles, para elegir en filtros y en lote. */
  protected readonly gruposVisibles = computed(() =>
    this.gruposTodos()
      .filter((g) => !g.oculto)
      .map((g) => ({ ...g, categorias: g.categorias.filter((c) => !c.oculta) }))
      .filter((g) => g.categorias.length > 0),
  );
  protected readonly hayFiltros = computed(() => hayFiltros(this.filtros()));

  /** Saldo de la cuenta filtrada o la suma de todas (milésimas enteras). */
  protected readonly saldoMostrado = computed(() => {
    const cuentaId = this.filtros().cuentaId;
    const saldos =
      cuentaId === null ? this.saldos() : this.saldos().filter((s) => s.cuentaId === cuentaId);
    return saldos.reduce(
      (suma, s) => ({
        saldo: suma.saldo + s.saldo,
        conciliado: suma.conciliado + s.saldoConciliado,
      }),
      { saldo: 0, conciliado: 0 },
    );
  });
  /** Id de la cuenta filtrada si está abierta (se puede conciliar), o `null`. */
  protected readonly cuentaConciliable = computed(() => {
    const cuentaId = this.filtros().cuentaId;
    const cuenta = this.cuentas().find((c) => c.id === cuentaId);
    return cuenta && !cuenta.cerrada ? cuenta.id : null;
  });
  protected readonly cuentaFiltrada = computed(() => {
    const cuentaId = this.filtros().cuentaId;
    return cuentaId === null ? null : (this.nombresCuenta().get(cuentaId) ?? null);
  });

  protected readonly seleccionadas = computed(() =>
    (this.pagina()?.contenido ?? []).filter((t) => this.seleccion().has(t.id)),
  );

  constructor() {
    // Cuentas y categorías (nombres y opciones): una vez, y de nuevo si una referencia desaparece.
    this.cargar(
      computed(() => ({ presupuestoId: this.presupuestoId(), r: this.recargasListas() })),
      (id) => forkJoin([this.cuentasServicio.listar(id), this.categoriasServicio.arbol(id)]),
      ([cuentas, grupos]) => {
        this.cuentas.set(cuentas);
        this.gruposTodos.set(grupos);
      },
    );
    // Beneficiarios (nombre actual en la tabla): al entrar y tras guardar desde el diálogo. Si
    // fallan, la columna muestra el texto de cada transacción.
    this.cargar(
      computed(() => ({
        presupuestoId: this.presupuestoId(),
        r: this.recargasBeneficiarios(),
      })),
      (id) => this.beneficiariosServicio.listar(id),
      (beneficiarios) => this.beneficiarios.set(beneficiarios),
      () => this.beneficiarios.set([]),
    );
    // La página: cada vez que cambian los filtros de la URL o se recarga. switchMap ignora las
    // respuestas atrasadas.
    this.cargar(
      computed(() => ({
        presupuestoId: this.presupuestoId(),
        filtros: this.filtros(),
        r: this.recargas(),
      })),
      (id, { filtros }) => this.servicio.listar(id, filtros),
      (pagina) => {
        this.pagina.set(pagina);
        this.estado.set('listo');
      },
      (error) => this.alFallarCarga(error),
    );
    // Saldos: al entrar y tras cada cambio.
    this.cargar(
      computed(() => ({ presupuestoId: this.presupuestoId(), r: this.recargas() })),
      (id) => this.servicio.saldos(id),
      (saldos) => this.saldos.set(saldos),
    );
    // Cambiar de página o de filtros (también con Atrás) vacía la selección.
    effect(() => {
      this.filtros();
      untracked(() => this.seleccion.set(new Set()));
    });
  }

  protected recargar(): void {
    this.recargas.update((v) => v + 1);
  }

  protected cambiarFiltros(filtros: FiltrosTransacciones): void {
    this.navegar({ ...filtros, pagina: 0 });
  }

  protected limpiarFiltros(): void {
    this.navegar(filtrosVacios(this.filtros().tamano));
  }

  protected paginar(evento: PageEvent): void {
    const cambiaTamano = evento.pageSize !== this.filtros().tamano;
    this.navegar({
      ...this.filtros(),
      pagina: cambiaTamano ? 0 : evento.pageIndex,
      tamano: evento.pageSize,
    });
  }

  protected agregar(): void {
    this.abrirDialogoTransaccion(null);
  }

  /** Con un filtro de cuenta abierta, esa cuenta es el origen propuesto. */
  protected agregarTransferencia(): void {
    const cuentaId = this.filtros().cuentaId;
    const abierta = cuentaId !== null && !this.cuentasCerradas().has(cuentaId);
    this.abrirDialogoTransferencia(null, abierta ? cuentaId : null);
  }

  protected alternarSeleccion(id: number): void {
    this.seleccion.update((actual) => {
      const nueva = new Set(actual);
      if (!nueva.delete(id)) {
        nueva.add(id);
      }
      return nueva;
    });
  }

  protected seleccionarPagina(todas: boolean): void {
    this.seleccion.set(
      todas
        ? new Set((this.pagina()?.contenido ?? []).slice(0, MAXIMO_LOTE).map((t) => t.id))
        : new Set(),
    );
  }

  protected limpiarSeleccion(): void {
    this.seleccion.set(new Set());
  }

  protected manejarAccion({ tipo, transaccion }: AccionFila): void {
    const id = this.presupuestoId();
    if (id === undefined) {
      return;
    }
    switch (tipo) {
      case 'editar':
        this.abrirDialogoTransaccion(transaccion);
        break;
      case 'duplicar':
        this.ejecutar(this.servicio.duplicar(id, transaccion.id), 'Transacción duplicada');
        break;
      case 'mover':
        this.abrir<DatosDialogoMoverCuenta, TransaccionResponse>(DialogoMoverCuentaComponent, {
          transaccion,
          cuentas: this.cuentas(),
        }).subscribe((movida) => movida && this.recargar());
        break;
      case 'aprobar':
        this.ejecutar(this.servicio.aprobar(id, transaccion.id));
        break;
      case 'estado':
        this.ejecutar(
          this.servicio.cambiarEstado(
            id,
            transaccion.id,
            transaccion.estado === 'NO_CONCILIADA' ? 'CONCILIADA' : 'NO_CONCILIADA',
          ),
        );
        break;
      case 'editarTransferencia':
        this.abrirDialogoTransferencia(transaccion.id, null);
        break;
      case 'borrarTransferencia':
        this.confirmar({
          titulo: 'Borrar transferencia',
          mensaje: MENSAJE_BORRAR_TRANSFERENCIA,
          confirmar: 'Borrar',
        }).subscribe((ok) => ok && this.ejecutar(this.transferencias.borrar(id, transaccion.id)));
        break;
      case 'borrar':
        this.confirmar({
          titulo: 'Borrar transacción',
          mensaje: '¿Borrar esta transacción? No se puede deshacer.',
          confirmar: 'Borrar',
        }).subscribe((ok) => ok && this.ejecutar(this.servicio.borrar(id, transaccion.id)));
        break;
    }
  }

  protected loteAprobar(): void {
    this.prepararLote('APROBAR');
  }

  protected loteCategorizar(categoriaId: number): void {
    this.prepararLote('CATEGORIZAR', categoriaId);
  }

  protected loteBorrar(): void {
    this.prepararLote('BORRAR');
  }

  /**
   * Excluye las filas a las que no se les puede aplicar la operación (el backend es todo o nada),
   * avisa cuántas omite y confirma antes de enviar; borrar se confirma siempre.
   */
  private prepararLote(operacion: OperacionLote, categoriaId?: number): void {
    const { aplicables, omitidas } = filtrarLote(this.seleccionadas(), operacion);
    if (aplicables.length === 0) {
      this.snackBar.open(MENSAJE_LOTE_SIN_APLICABLES, 'Cerrar', { duration: 6000 });
      return;
    }
    const enviar = () => this.enviarLote(operacion, aplicables, categoriaId);
    if (omitidas.length === 0 && operacion !== 'BORRAR') {
      enviar();
      return;
    }
    const verbo = NOMBRE_OPERACION[operacion];
    const omision =
      omitidas.length > 0
        ? `Se omitirán ${omitidas.length} (patas de transferencia, divididas o reconciliadas). `
        : '';
    this.confirmar({
      titulo: operacion === 'BORRAR' ? 'Borrar transacciones' : 'Aplicar en lote',
      mensaje: `${omision}¿${verbo[0].toUpperCase()}${verbo.slice(1)} ${aplicables.length} ${
        aplicables.length === 1 ? 'transacción' : 'transacciones'
      }?`,
      confirmar: operacion === 'BORRAR' ? 'Borrar' : 'Continuar',
    }).subscribe((ok) => ok && enviar());
  }

  private enviarLote(
    operacion: OperacionLote,
    transacciones: TransaccionResponse[],
    categoriaId?: number,
  ): void {
    const id = this.presupuestoId();
    if (id === undefined) {
      return;
    }
    const ids = transacciones.slice(0, MAXIMO_LOTE).map((t) => t.id);
    const solicitud =
      categoriaId === undefined ? { ids, operacion } : { ids, operacion, categoriaId };
    this.servicio.lote(id, solicitud).subscribe({
      next: ({ afectadas }) => {
        this.snackBar.open(
          `${afectadas} ${afectadas === 1 ? 'transacción actualizada' : 'transacciones actualizadas'}`,
          'Cerrar',
          { duration: 4000 },
        );
        this.limpiarSeleccion();
        this.recargar();
      },
      error: (error: unknown) => this.alFallarAccion(error),
    });
  }

  private abrirDialogoTransaccion(transaccion: TransaccionResponse | null): void {
    this.abrir<DatosDialogoTransaccion, ResultadoDialogoTransaccion>(
      DialogoTransaccionComponent,
      { transaccion, cuentas: this.cuentas(), grupos: this.gruposTodos() },
      '640px',
    ).subscribe((resultado) => {
      if (resultado) {
        // Guardar puede haber creado un beneficiario nuevo.
        this.recargasBeneficiarios.update((v) => v + 1);
      }
      if (resultado?.tipo === 'guardada') {
        this.recargar();
      } else if (resultado?.tipo === 'recargar') {
        this.recargasListas.update((v) => v + 1);
        this.recargar();
      }
    });
  }

  /** Crear (`transaccionId` nulo) o editar una transferencia por el id de una de sus patas. */
  private abrirDialogoTransferencia(transaccionId: number | null, cuentaOrigenId: number | null) {
    this.abrir<DatosDialogoTransferencia, ResultadoDialogoTransaccion>(
      DialogoTransferenciaComponent,
      { transaccionId, cuentaOrigenId, cuentas: this.cuentas(), grupos: this.gruposTodos() },
      '640px',
    ).subscribe((resultado) => {
      if (resultado?.tipo === 'recargar') {
        this.recargasListas.update((v) => v + 1);
      }
      if (resultado) {
        this.recargar();
      }
    });
  }

  private confirmar(datos: DatosDialogoConfirmacion): Observable<boolean | undefined> {
    return this.abrir<DatosDialogoConfirmacion, boolean>(DialogoConfirmacionComponent, datos);
  }

  private abrir<D, R>(
    componente: ComponentType<unknown>,
    datos: D,
    ancho = '440px',
  ): Observable<R | undefined> {
    return this.dialog.open<unknown, D, R>(componente, { data: datos, width: ancho }).afterClosed();
  }

  private ejecutar(peticion: Observable<unknown>, exito?: string): void {
    peticion.subscribe({
      next: () => {
        if (exito) {
          this.snackBar.open(exito, 'Cerrar', { duration: 4000 });
        }
        this.recargar();
      },
      error: (error: unknown) => this.alFallarAccion(error),
    });
  }

  private alFallarAccion(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.snackBar.open(MENSAJE_ACCION_NO_PERMITIDA, 'Cerrar', { duration: 6000 });
    } else if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.snackBar.open(MENSAJE_TRANSACCION_INEXISTENTE, 'Cerrar', { duration: 6000 });
    } else {
      this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    }
    this.recargar();
  }

  private alFallarCarga(error: unknown): void {
    if (leerProblemaApi(error)?.status === 401) {
      return;
    }
    if (this.pagina() === null) {
      this.estado.set('error');
    }
    this.snackBar
      .open(MENSAJE_ERROR_GENERICO, 'Reintentar', { duration: 6000 })
      .onAction()
      .subscribe(() => this.recargar());
  }

  private navegar(filtros: FiltrosTransacciones): void {
    void this.router.navigate([], { relativeTo: this.ruta, queryParams: aQueryParams(filtros) });
  }

  /**
   * Carga reactiva: cada vez que cambia `disparador` (con presupuesto), pide y aplica el
   * resultado, descartando respuestas atrasadas.
   */
  private cargar<P extends { presupuestoId: number | undefined }, T>(
    disparador: () => P,
    pedir: (presupuestoId: number, parametros: P) => Observable<T>,
    aplicar: (valor: T) => void,
    alFallar: (error: unknown) => void = () => undefined,
  ): void {
    toObservable(computed(disparador))
      .pipe(
        filter((p) => p.presupuestoId !== undefined),
        tap(() => {
          if (this.pagina() === null) {
            this.estado.set('cargando');
          }
        }),
        switchMap((p) =>
          pedir(p.presupuestoId as number, p).pipe(
            catchError((error: unknown) => {
              alFallar(error);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(aplicar);
  }
}
