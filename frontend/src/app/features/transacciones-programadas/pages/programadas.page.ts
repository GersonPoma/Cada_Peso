import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, catchError, filter, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  DatosConfirmarBorrado,
  DialogoConfirmarBorradoComponent,
} from '../components/dialogo-confirmar-borrado.component';
import { DialogoProgramadaComponent } from '../components/dialogo-programada.component';
import { CuentaLectura } from '../models/cuenta-lectura.model';
import {
  DatosDialogoProgramada,
  ResultadoDialogoProgramada,
} from '../models/datos-dialogo-programada.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { TransaccionProgramadaResponse } from '../models/transaccion-programada-response.model';
import { CategoriaLecturaService } from '../services/categoria-lectura.service';
import { CuentaLecturaService } from '../services/cuenta-lectura.service';
import { accionError } from '../services/errores-programada';
import {
  MENSAJE_BORRADA,
  MENSAJE_ERROR_CARGA,
  MENSAJE_PAUSADA,
  MENSAJE_REANUDADA,
  MENSAJE_SIN_PROGRAMADAS,
  PREFIJO_ULTIMO_ERROR,
  SUGERENCIA_ULTIMO_ERROR,
} from '../services/mensajes-programada';
import { filaProgramada, textoResultadoGeneracion } from '../services/presentacion-programada';
import { TransaccionProgramadaService } from '../services/transaccion-programada.service';

/** Lo que carga la pantalla de una vez. */
interface Datos {
  programadas: TransaccionProgramadaResponse[];
  cuentas: CuentaLectura[];
  grupos: GrupoCategoriasLectura[];
}

type Estado = { tipo: 'cargando' } | { tipo: 'listo'; datos: Datos } | { tipo: 'error' };

/**
 * Transacciones programadas del presupuesto: lista con estado y frecuencia legibles, crear y
 * editar en un diálogo, pausar, reanudar, borrar y `Generar ahora`. La próxima fecha y el estado
 * son siempre los del servidor; el calendario de ocurrencias no se calcula aquí.
 */
@Component({
  selector: 'app-programadas',
  imports: [
    FechaPipe,
    MatButton,
    MatIcon,
    MatIconButton,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
    MatProgressSpinner,
    MontoPipe,
  ],
  templateUrl: './programadas.page.html',
  styleUrl: './programadas.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgramadasPage {
  private readonly servicio = inject(TransaccionProgramadaService);
  private readonly cuentasServicio = inject(CuentaLecturaService);
  private readonly categoriasServicio = inject(CategoriaLecturaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly sinProgramadas = MENSAJE_SIN_PROGRAMADAS;
  protected readonly errorCarga = MENSAJE_ERROR_CARGA;
  protected readonly prefijoError = PREFIJO_ULTIMO_ERROR;
  protected readonly sugerenciaError = SUGERENCIA_ULTIMO_ERROR;

  protected readonly presupuestoId = computed(() => this.presupuestoActivo.presupuesto()?.id);
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? 'USD');
  private readonly recargas = signal(0);

  /** `switchMap` descarta la respuesta de una carga anterior si llega otra recarga. */
  protected readonly estado = toSignal(
    toObservable(
      computed(() => ({ presupuestoId: this.presupuestoId(), recargas: this.recargas() })),
    ).pipe(
      filter(({ presupuestoId }) => presupuestoId !== undefined),
      switchMap(({ presupuestoId }) => this.cargar(presupuestoId as number)),
    ),
    { initialValue: { tipo: 'cargando' } as Estado },
  );

  private readonly datos = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'listo' ? estado.datos : null;
  });

  /** Las plantillas de la última carga, con los cambios de pausar y reanudar aplicados. */
  private readonly programadas = linkedSignal(() => this.datos()?.programadas ?? []);

  private readonly cuentasPorId = computed(
    () => new Map((this.datos()?.cuentas ?? []).map((c) => [c.id, c])),
  );
  private readonly categoriasPorId = computed(
    () =>
      new Map(
        (this.datos()?.grupos ?? []).flatMap((g) => g.categorias.map((c) => [c.id, c.nombre])),
      ),
  );

  protected readonly filas = computed(() =>
    this.programadas().map((p) => filaProgramada(p, this.cuentasPorId(), this.categoriasPorId())),
  );

  /** Ids de las programadas con una acción en curso: sus botones quedan deshabilitados. */
  protected readonly ocupadas = signal<ReadonlySet<number>>(new Set());
  protected readonly generando = signal(false);
  protected readonly resultadoGeneracion = signal<string | null>(null);

  protected recargar(): void {
    this.recargas.update((n) => n + 1);
  }

  protected crear(): void {
    this.abrirDialogo(null);
  }

  protected editar(programada: TransaccionProgramadaResponse): void {
    this.abrirDialogo(programada);
  }

  protected pausar(programada: TransaccionProgramadaResponse): void {
    this.accion(programada.id, (id, p) => this.servicio.pausar(p, id), MENSAJE_PAUSADA);
  }

  protected reanudar(programada: TransaccionProgramadaResponse): void {
    this.accion(programada.id, (id, p) => this.servicio.reanudar(p, id), MENSAJE_REANUDADA);
  }

  protected borrar(programada: TransaccionProgramadaResponse, nombre: string): void {
    this.dialog
      .open<DialogoConfirmarBorradoComponent, DatosConfirmarBorrado, boolean>(
        DialogoConfirmarBorradoComponent,
        { data: { nombre }, width: '440px', maxWidth: '95vw' },
      )
      .afterClosed()
      .subscribe((confirmado) => {
        if (!confirmado) {
          return;
        }
        this.accion(
          programada.id,
          (id, p) => this.servicio.borrar(p, id).pipe(map(() => null)),
          MENSAJE_BORRADA,
        );
      });
  }

  protected generar(): void {
    const presupuestoId = this.presupuestoId();
    if (presupuestoId === undefined || this.generando()) {
      return;
    }
    this.generando.set(true);
    this.resultadoGeneracion.set(null);
    this.servicio.generar(presupuestoId).subscribe({
      next: (resultado) => {
        this.generando.set(false);
        const texto = textoResultadoGeneracion(resultado);
        this.resultadoGeneracion.set(texto);
        this.avisar(texto);
        this.recargar();
      },
      error: (error: unknown) => {
        this.generando.set(false);
        if (leerProblemaApi(error)?.status !== 401) {
          this.avisar(MENSAJE_ERROR_GENERICO);
        }
      },
    });
  }

  protected ocupada(id: number): boolean {
    return this.ocupadas().has(id);
  }

  private abrirDialogo(original: TransaccionProgramadaResponse | null): void {
    const datos = this.datos();
    if (datos === null) {
      return;
    }
    this.dialog
      .open<DialogoProgramadaComponent, DatosDialogoProgramada, ResultadoDialogoProgramada>(
        DialogoProgramadaComponent,
        {
          data: { cuentas: datos.cuentas, grupos: datos.grupos, original },
          width: '600px',
          maxWidth: '95vw',
        },
      )
      .afterClosed()
      .subscribe((resultado) => {
        if (resultado) {
          this.recargar();
        }
      });
  }

  /**
   * Pausar, reanudar o borrar: deshabilita las acciones de esa programada mientras espera y, al
   * terminar, reemplaza la fila con la respuesta (o la quita si es `null`).
   */
  private accion(
    id: number,
    peticion: (
      id: number,
      presupuestoId: number,
    ) => Observable<TransaccionProgramadaResponse | null>,
    exito: string,
  ): void {
    const presupuestoId = this.presupuestoId();
    if (presupuestoId === undefined || this.ocupada(id)) {
      return;
    }
    this.marcar(id, true);
    peticion(id, presupuestoId).subscribe({
      next: (respuesta) => {
        this.marcar(id, false);
        this.programadas.update((lista) =>
          respuesta === null
            ? lista.filter((p) => p.id !== id)
            : lista.map((p) => (p.id === id ? respuesta : p)),
        );
        this.avisar(exito);
      },
      error: (error: unknown) => {
        this.marcar(id, false);
        const accion = accionError(error);
        if (accion.accion === 'ninguna') {
          return;
        }
        if (accion.accion === 'recargar') {
          this.avisar(accion.mensaje);
          this.recargar();
          return;
        }
        this.avisar(MENSAJE_ERROR_GENERICO);
      },
    });
  }

  private marcar(id: number, ocupada: boolean): void {
    this.ocupadas.update((actuales) => {
      const nuevas = new Set(actuales);
      if (ocupada) {
        nuevas.add(id);
      } else {
        nuevas.delete(id);
      }
      return nuevas;
    });
  }

  private cargar(presupuestoId: number): Observable<Estado> {
    return forkJoin({
      programadas: this.servicio.listar(presupuestoId),
      cuentas: this.cuentasServicio.listar(presupuestoId),
      grupos: this.categoriasServicio.arbol(presupuestoId),
    }).pipe(
      map((datos): Estado => ({ tipo: 'listo', datos })),
      catchError(() => of<Estado>({ tipo: 'error' })),
      startWith<Estado>({ tipo: 'cargando' }),
    );
  }

  private avisar(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', { duration: 6000, politeness: 'polite' });
  }
}
