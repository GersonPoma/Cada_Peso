import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatListItem, MatListItemIcon, MatListItemTitle, MatNavList } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  ActivatedRoute,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { map } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { CabeceraComponent } from '../../../shared/cabecera/cabecera.component';
import { DialogoPresupuestoComponent } from '../components/dialogo-presupuesto.component';
import { DatosDialogoPresupuesto } from '../models/datos-dialogo-presupuesto.model';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { SeccionPresupuesto } from '../models/seccion-presupuesto.model';
import { PresupuestoService } from '../services/presupuesto.service';

type Estado = 'cargando' | 'listo' | 'error';

/** Enlace del menú lateral: la ruta hija (relativa al layout) y lo que se muestra. */
interface EnlaceSeccion {
  ruta: string;
  seccion: SeccionPresupuesto;
}

/** Por debajo de 960 px el menú lateral se oculta y se abre sobre el contenido. */
const PANTALLA_ESTRECHA = [Breakpoints.XSmall, Breakpoints.Small];

/**
 * Pantalla de un presupuesto (`/presupuestos/:presupuestoId`): cabecera con el selector de
 * presupuesto, su gestión y el cierre de sesión; menú lateral con las secciones declaradas en las
 * rutas hijas (`data.seccion`); y el contenido de la sección elegida. Fija el presupuesto activo
 * según la URL y vuelve a `/` si el id no es de la persona.
 */
@Component({
  selector: 'app-layout-presupuesto',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatButton,
    MatFormField,
    MatIcon,
    MatIconButton,
    MatListItem,
    MatListItemIcon,
    MatListItemTitle,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatNavList,
    MatOption,
    MatProgressSpinner,
    MatSelect,
    MatSidenav,
    MatSidenavContainer,
    MatSidenavContent,
    CabeceraComponent,
  ],
  templateUrl: './layout-presupuesto.page.html',
  styleUrl: './layout-presupuesto.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutPresupuestoPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servicio = inject(PresupuestoService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly sesion = inject(SesionService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly estado = signal<Estado>('cargando');
  protected readonly presupuestos = signal<PresupuestoResponse[]>([]);
  private readonly idEnUrl = signal<string | null>(null);

  /** El presupuesto de la URL, si está en la lista de la persona. */
  protected readonly presupuestoDeUrl = computed(() => {
    const id = this.idEnUrl();
    return this.presupuestos().find((presupuesto) => String(presupuesto.id) === id) ?? null;
  });

  /** Las secciones solo se pintan con el presupuesto activo ya fijado al de la URL. */
  protected readonly contenidoListo = computed(() => {
    const deUrl = this.presupuestoDeUrl();
    return deUrl !== null && this.presupuestoActivo.presupuesto()?.id === deUrl.id;
  });

  protected readonly estrecha = toSignal(
    inject(BreakpointObserver)
      .observe(PANTALLA_ESTRECHA)
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  protected readonly secciones: EnlaceSeccion[] = (this.ruta.routeConfig?.children ?? []).flatMap(
    (hija) => {
      const seccion = hija.data?.['seccion'] as SeccionPresupuesto | undefined;
      return seccion ? [{ ruta: hija.path ?? '', seccion }] : [];
    },
  );

  constructor() {
    this.ruta.paramMap.pipe(takeUntilDestroyed()).subscribe((parametros) => {
      this.idEnUrl.set(parametros.get('presupuestoId'));
      this.sincronizar();
    });
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.servicio
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.presupuestos.set(lista);
          this.estado.set('listo');
          this.sincronizar();
        },
        error: (error: unknown) => {
          // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
          if (leerProblemaApi(error)?.status === 401) {
            return;
          }
          this.estado.set('error');
          this.snackBar
            .open(MENSAJE_ERROR_GENERICO, 'Reintentar', { duration: 6000 })
            .onAction()
            .subscribe(() => this.cargar());
        },
      });
  }

  protected cambiarPresupuesto(id: number): void {
    void this.router.navigate(['/presupuestos', id]);
  }

  protected crear(): void {
    this.abrirDialogo({ modo: 'crear' }, (creado) => {
      this.guardarOrdenada([...this.presupuestos(), creado]);
      this.cambiarPresupuesto(creado.id);
    });
  }

  protected renombrar(): void {
    const actual = this.presupuestoDeUrl();
    if (!actual) {
      return;
    }
    const datos: DatosDialogoPresupuesto = {
      modo: 'renombrar',
      presupuesto: { id: actual.id, nombre: actual.nombre },
    };
    this.abrirDialogo(datos, (renombrado) => {
      this.guardarOrdenada(
        this.presupuestos().map((p) => (p.id === renombrado.id ? renombrado : p)),
      );
      this.sincronizar();
    });
  }

  protected alElegirSeccion(menu: MatSidenav): void {
    if (this.estrecha()) {
      void menu.close();
    }
  }

  protected cerrarSesion(): void {
    this.sesion.cerrar();
    void this.router.navigateByUrl('/login');
  }

  /** Fija el presupuesto de la URL como activo, o vuelve a `/` si no es de la persona. */
  private sincronizar(): void {
    if (this.estado() !== 'listo' || this.idEnUrl() === null) {
      return;
    }
    const presupuesto = this.presupuestoDeUrl();
    if (presupuesto) {
      this.presupuestoActivo.fijar(presupuesto);
    } else {
      void this.router.navigate(['/'], { replaceUrl: true });
    }
  }

  private abrirDialogo(
    datos: DatosDialogoPresupuesto,
    alTerminar: (presupuesto: PresupuestoResponse) => void,
  ): void {
    this.dialog
      .open<DialogoPresupuestoComponent, DatosDialogoPresupuesto, PresupuestoResponse>(
        DialogoPresupuestoComponent,
        { data: datos, width: '400px' },
      )
      .afterClosed()
      .subscribe((presupuesto) => {
        if (presupuesto) {
          alTerminar(presupuesto);
        }
      });
  }

  /** Guarda la lista ordenada por nombre sin distinguir mayúsculas, como la ordena el backend. */
  private guardarOrdenada(lista: PresupuestoResponse[]): void {
    const ordenada = [...lista].sort((a, b) =>
      a.nombre.localeCompare(b.nombre, undefined, { sensitivity: 'base' }),
    );
    this.presupuestos.set(ordenada);
  }
}
