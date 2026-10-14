import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';

import { BookService } from '../../core/api/book.service';
import { Book, Page } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination.component';
import { errorMessage } from '../../shared/error-message';

type SortField = 'title' | 'author' | 'genre' | 'publishedYear' | 'availableCopies';

/**
 * Librarian table of all books with server-side search, genre filter, sorting and paging, plus edit/delete.
 *
 * Delete uses an INLINE confirmation ("Delete? Yes / No" inside the row) instead of window.confirm(): it is
 * styleable, accessible, testable, and doesn't block the browser. If copies are still on loan, the server answers
 * 409 and the message is shown above the table.
 */
@Component({
  selector: 'app-book-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, PaginationComponent],
  templateUrl: './book-list.component.html',
})
export class BookListComponent implements OnInit {
  private readonly api = inject(BookService);
  private readonly destroyRef = inject(DestroyRef);

  readonly search = new FormControl('', { nonNullable: true });
  readonly genre = new FormControl('', { nonNullable: true });

  readonly pageSize = 15;
  readonly pageIndex = signal(0);
  readonly sortField = signal<SortField>('title');
  readonly sortDir = signal<'asc' | 'desc'>('asc');

  readonly page = signal<Page<Book> | null>(null);
  readonly genres = signal<string[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Id of the row currently asking "Delete?" (null = none). */
  readonly confirmingDeleteId = signal<number | null>(null);

  ngOnInit(): void {
    this.api.genres().subscribe({ next: (g) => this.genres.set(g) });
    merge(this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()), this.genre.valueChanges)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.goToPage(0));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api
      .list({
        q: this.search.value,
        genre: this.genre.value,
        page: this.pageIndex(),
        size: this.pageSize,
        sort: `${this.sortField()},${this.sortDir()}`,
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err));
          this.loading.set(false);
        },
      });
  }

  goToPage(index: number): void {
    this.pageIndex.set(index);
    this.load();
  }

  /** Click a header to sort by it; click again to flip the direction. */
  sortBy(field: SortField): void {
    if (this.sortField() === field) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDir.set('asc');
    }
    this.goToPage(0);
  }

  sortIndicator(field: SortField): string {
    if (this.sortField() !== field) return '';
    return this.sortDir() === 'asc' ? '▲' : '▼';
  }

  delete(book: Book): void {
    this.error.set(null);
    this.api.delete(book.id).subscribe({
      next: () => {
        this.confirmingDeleteId.set(null);
        // Deleted the last row on a later page? Step back one page so we don't show an empty page.
        const p = this.page();
        if (p && p.content.length === 1 && this.pageIndex() > 0) {
          this.pageIndex.update((i) => i - 1);
        }
        this.load();
      },
      error: (err) => {
        this.confirmingDeleteId.set(null);
        this.error.set(errorMessage(err, 'Could not delete this book'));
      },
    });
  }
}
