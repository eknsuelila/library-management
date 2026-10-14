import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Chart, registerables } from 'chart.js';

import { StatsService } from '../../core/api/stats.service';
import { Stats } from '../../core/models';
import { errorMessage } from '../../shared/error-message';

// Chart.js is tree-shakable: chart types, scales and plugins must be registered before use.
// `registerables` registers all of them (simplest; costs a few extra KB in this lazily-loaded chunk only).
Chart.register(...registerables);

const PRIMARY = '#0f766e';

/**
 * Librarian dashboard: headline numbers plus three charts, all from a single GET /api/stats call.
 *
 * Lifecycle hooks used:
 *  - ngOnInit:        component created -> start loading data
 *  - ngAfterViewInit: template rendered -> <canvas> elements can exist now
 *  - ngOnDestroy:     component removed -> destroy charts so they release their canvas and listeners
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, CurrencyPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(StatsService);

  // @ViewChild gives the class a reference to the element marked #name in the template.
  @ViewChild('monthlyChart') monthlyCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('topChart') topCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('stockChart') stockCanvas?: ElementRef<HTMLCanvasElement>;

  readonly stats = signal<Stats | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Charts have different generic types (Chart<'bar'>, Chart<'doughnut'>); all we need later is destroy().
  private charts: Pick<Chart, 'destroy'>[] = [];
  private viewReady = false;

  ngOnInit(): void {
    this.api.get().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
        this.renderCharts();
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.renderCharts();
  }

  ngOnDestroy(): void {
    this.charts.forEach((c) => c.destroy());
  }

  /**
   * Draws the charts once BOTH the data has arrived AND the view exists - whichever happens last calls this
   * successfully. setTimeout waits one tick so the @if blocks have actually put the <canvas> elements in the DOM.
   */
  private renderCharts(): void {
    const stats = this.stats();
    if (!stats || !this.viewReady) {
      return;
    }
    setTimeout(() => {
      this.charts.forEach((c) => c.destroy());
      this.charts = [];
      if (this.monthlyCanvas) this.charts.push(this.buildMonthlyChart(this.monthlyCanvas.nativeElement, stats));
      if (this.topCanvas) this.charts.push(this.buildTopChart(this.topCanvas.nativeElement, stats));
      if (this.stockCanvas) this.charts.push(this.buildStockChart(this.stockCanvas.nativeElement, stats));
    });
  }

  /** Vertical bars: loans started per month over the last 6 months. */
  private buildMonthlyChart(canvas: HTMLCanvasElement, stats: Stats) {
    const fmt = new Intl.DateTimeFormat(undefined, { month: 'short', year: '2-digit' });
    return new Chart(canvas, {
      type: 'bar',
      data: {
        // "2026-10" + "-01T00:00" -> parsed in local time, so the label shows the right month.
        labels: stats.loansPerMonth.map((m) => fmt.format(new Date(m.month + '-01T00:00'))),
        datasets: [{ label: 'Loans', data: stats.loansPerMonth.map((m) => m.count), backgroundColor: PRIMARY, borderRadius: 6, maxBarThickness: 40 }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } }, x: { grid: { display: false } } },
      },
    });
  }

  /** Horizontal bars (indexAxis: 'y'): the 5 most borrowed titles; long titles fit better sideways. */
  private buildTopChart(canvas: HTMLCanvasElement, stats: Stats) {
    return new Chart(canvas, {
      type: 'bar',
      data: {
        labels: stats.topBorrowed.map((b) => b.title),
        datasets: [{ label: 'Times borrowed', data: stats.topBorrowed.map((b) => b.loans), backgroundColor: '#7c3aed', borderRadius: 6, maxBarThickness: 28 }],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { grid: { display: false } } },
      },
    });
  }

  /** Doughnut: copies on the shelf vs. on loan (split into on-time and overdue). */
  private buildStockChart(canvas: HTMLCanvasElement, stats: Stats) {
    const onTime = stats.copiesOnLoan - stats.overdueLoans;
    return new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: ['On the shelf', 'On loan', 'Overdue'],
        datasets: [{
          data: [stats.totalCopies - stats.copiesOnLoan, onTime, stats.overdueLoans],
          backgroundColor: ['#22c55e', '#0ea5e9', '#ef4444'],
          borderWidth: 2,
        }],
      },
      options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { position: 'bottom' } } },
    });
  }
}
