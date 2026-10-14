import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Page } from '../core/models';

/**
 * "← Prev  Page 2 of 5  Next →" controls, reused by every paged list.
 *
 * Data flows DOWN through @Input (the current page from the server) and UP through @Output: when a button is
 * clicked the component emits the requested page index, and the parent decides how to load it:
 *   <app-pagination [page]="page()" (pageChange)="goToPage($event)" />
 * The component is hidden when everything fits on one page.
 */
@Component({
  selector: 'app-pagination',
  standalone: true,
  template: `
    @if (page && page.totalPages > 1) {
      <div class="pagination">
        <button class="btn btn-secondary btn-sm" type="button" [disabled]="page.page === 0"
                (click)="pageChange.emit(page.page - 1)">← Prev</button>
        <span class="muted">Page {{ page.page + 1 }} of {{ page.totalPages }}</span>
        <button class="btn btn-secondary btn-sm" type="button" [disabled]="page.page >= page.totalPages - 1"
                (click)="pageChange.emit(page.page + 1)">Next →</button>
      </div>
    }
  `,
  styles: `
    .pagination { display: flex; justify-content: center; align-items: center; gap: 16px; padding: 12px; }
  `,
})
export class PaginationComponent {
  @Input() page: Page<unknown> | null = null;
  @Output() readonly pageChange = new EventEmitter<number>();
}
