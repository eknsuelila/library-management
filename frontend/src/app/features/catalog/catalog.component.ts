import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { BookService } from '../../core/api/book.service';
import { LoanService } from '../../core/api/loan.service';
import { Book, Loan, Page } from '../../core/models';
import { BookCoverComponent } from '../../shared/book-cover.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { errorMessage } from '../../shared/error-message';
import { borrowBlockReason } from './borrow-rules';

/** A book plus the precomputed reason (if any) why the member can't borrow it. */
interface CatalogCard {
  book: Book;
  blockedReason: string | null;
}

/**
 * The catalog: a searchable, filterable grid of book cards.
 *
 * - Members see a "Borrow" button on each card, disabled with an explanation when a lending rule would be broken.
 * - Librarians see an "Edit" link instead (they lend books from the Loans page).
 *
 * Search, genre and "available only" are evaluated by the SERVER (GET /api/books?q=&genre=&availableOnly=),
 * so the browser only downloads one page of results at a time.
 */
@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BookCoverComponent, PaginationComponent],
  templateUrl: './catalog.component.html',
  styleUrl: './catalog.component.scss',
})
export class CatalogComponent implements OnInit {
  private readonly books = inject(BookService);
  private readonly loans = inject(LoanService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  // ----- Filter inputs: FormControls expose a valueChanges Observable we can react to. -----
  readonly search = new FormControl('', { nonNullable: true });
  readonly genre = new FormControl('', { nonNullable: true });
  readonly availableOnly = new FormControl(false, { nonNullable: true });

  readonly pageSize = 12;
  readonly pageIndex = signal(0);

  // ----- Data / UI state as signals -----
  readonly page = signal<Page<Book> | null>(null);
  readonly genres = signal<string[]>([]);
  readonly myLoans = signal<Loan[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  /** Id of the book whose Borrow request is in flight (disables that button to prevent double clicks). */
  readonly borrowingId = signal<number | null>(null);

  /** Loans that still count against the member (not yet returned). */
  readonly openLoans = computed(() => this.myLoans().filter((l) => l.status !== 'RETURNED'));

  /**
   * Cards to render. computed() re-runs only when `page` or `openLoans` change, instead of re-evaluating
   * the rules for every card on every change-detection cycle.
   */
  readonly cards = computed<CatalogCard[]>(() =>
    (this.page()?.content ?? []).map((book) => ({
      book,
      blockedReason: this.auth.isMember() ? borrowBlockReason(book, this.openLoans()) : null,
    })),
  );

  ngOnInit(): void {
    this.books.genres().subscribe({ next: (g) => this.genres.set(g) });
    if (this.auth.isMember()) {
      this.refreshMyLoans();
    }

    // Typing: wait 300 ms after the last keystroke, ignore if the text didn't change.
    const search$ = this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged());
    // merge() combines the three streams: any filter change reloads from page 1.
    // takeUntilDestroyed() unsubscribes automatically when the component is destroyed (no memory leaks).
    merge(search$, this.genre.valueChanges, this.availableOnly.valueChanges)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.goToPage(0));

    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.books
      .list({
        q: this.search.value,
        genre: this.genre.value,
        availableOnly: this.availableOnly.value || undefined,
        page: this.pageIndex(),
        size: this.pageSize,
        sort: 'title,asc',
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

  clearFilters(): void {
    // emitEvent: false avoids three separate reloads; we reload once ourselves.
    this.search.setValue('', { emitEvent: false });
    this.genre.setValue('', { emitEvent: false });
    this.availableOnly.setValue(false, { emitEvent: false });
    this.goToPage(0);
  }

  borrow(book: Book): void {
    this.borrowingId.set(book.id);
    this.error.set(null);
    this.notice.set(null);
    this.loans.borrow(book.id).subscribe({
      next: (loan) => {
        this.borrowingId.set(null);
        this.notice.set(`You borrowed “${loan.bookTitle}”. It is due back on ${this.formatDate(loan.dueDate)}.`);
        // Update local state instead of refetching everything: one copy fewer, one loan more.
        this.page.update((p) =>
          p ? { ...p, content: p.content.map((b) => (b.id === book.id ? { ...b, availableCopies: b.availableCopies - 1 } : b)) } : p,
        );
        this.myLoans.update((loans) => [loan, ...loans]);
      },
      error: (err) => {
        this.borrowingId.set(null);
        // The server's 409 message explains which rule failed (e.g. someone else took the last copy).
        this.error.set(errorMessage(err, 'Could not borrow this book'));
        this.load();
        this.refreshMyLoans();
      },
    });
  }

  private refreshMyLoans(): void {
    this.loans.mine().subscribe({ next: (loans) => this.myLoans.set(loans) });
  }

  private formatDate(iso: string): string {
    // "T00:00" parses the date in LOCAL time; a bare "2026-10-28" would be UTC and could show the previous day.
    return new Date(iso + 'T00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  }
}
