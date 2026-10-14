import { Component, DestroyRef, Input, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { BookService } from '../../core/api/book.service';
import { LoanService } from '../../core/api/loan.service';
import { MemberService } from '../../core/api/member.service';
import { Book, LOAN_STATUSES, Loan, LoanStatus, Member, Page } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination.component';
import { LoanStatusPipe, dueLabel } from '../../shared/loan-status.pipe';
import { errorMessage } from '../../shared/error-message';

/**
 * Librarian loan desk:
 *  - "Lend a book" form: pick a member and an available book -> POST /api/loans { bookId, memberId }.
 *    All lending rules (3-book limit, overdue block, ...) are enforced by the server, and its 409 message is shown.
 *  - Table of every loan, filterable by status (Active / Overdue / Returned), with "Mark returned".
 *
 * The status filter can be pre-selected from the URL (/loans?status=OVERDUE, used by the dashboard's
 * "Overdue" card): withComponentInputBinding() also binds QUERY parameters to @Input()s.
 */
@Component({
  selector: 'app-loan-admin',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, CurrencyPipe, PaginationComponent, LoanStatusPipe],
  templateUrl: './loan-admin.component.html',
  styleUrl: './loan-admin.component.scss',
})
export class LoanAdminComponent implements OnInit {
  private readonly loansApi = inject(LoanService);
  private readonly booksApi = inject(BookService);
  private readonly membersApi = inject(MemberService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  /** Optional ?status=... query parameter. A setter so the value goes straight into the filter control. */
  @Input() set status(value: string | undefined) {
    const valid = LOAN_STATUSES.includes(value as LoanStatus);
    this.statusFilter.setValue(valid ? (value as LoanStatus) : '', { emitEvent: false });
  }

  /** Optional ?memberId=...&memberName=... (from the Members page) to show one member's loans. */
  @Input() set memberId(value: string | undefined) {
    this.memberFilter.set(value ? Number(value) : null);
  }
  @Input() memberName?: string;

  readonly memberFilter = signal<number | null>(null);

  readonly statuses = LOAN_STATUSES;
  readonly statusFilter = new FormControl<LoanStatus | ''>('', { nonNullable: true });

  readonly lendForm = this.fb.group({
    memberId: this.fb.control<number | null>(null, Validators.required),
    bookId: this.fb.control<number | null>(null, Validators.required),
  });

  readonly pageSize = 15;
  readonly pageIndex = signal(0);
  readonly page = signal<Page<Loan> | null>(null);
  readonly members = signal<Member[]>([]);
  readonly availableBooks = signal<Book[]>([]);
  readonly loading = signal(false);
  readonly lending = signal(false);
  readonly busyId = signal<number | null>(null);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly dueLabel = dueLabel;

  ngOnInit(): void {
    this.statusFilter.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.goToPage(0));
    this.loadLendOptions();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loansApi
      .list({
        status: this.statusFilter.value,
        memberId: this.memberFilter(),
        page: this.pageIndex(),
        size: this.pageSize,
        // Overdue list: most overdue first. Otherwise newest loans first.
        sort: this.statusFilter.value === 'OVERDUE' ? 'dueDate,asc' : 'borrowedDate,desc',
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

  clearMemberFilter(): void {
    this.memberFilter.set(null);
    this.memberName = undefined;
    this.goToPage(0);
  }

  lend(): void {
    const { memberId, bookId } = this.lendForm.getRawValue();
    if (this.lendForm.invalid || memberId === null || bookId === null) {
      this.lendForm.markAllAsTouched();
      return;
    }
    this.lending.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.loansApi.borrow(bookId, memberId).subscribe({
      next: (loan) => {
        this.notice.set(`Lent “${loan.bookTitle}” to ${loan.memberName}, due ${loan.dueDate}.`);
        this.lendForm.reset();
        this.lending.set(false);
        this.loadLendOptions();
        this.goToPage(0);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Could not lend this book'));
        this.lending.set(false);
      },
    });
  }

  markReturned(loan: Loan): void {
    this.busyId.set(loan.id);
    this.error.set(null);
    this.notice.set(null);
    this.loansApi.returnLoan(loan.id).subscribe({
      next: (updated) => {
        this.notice.set(
          `“${updated.bookTitle}” returned by ${updated.memberName}` +
            (updated.fine > 0 ? ` with a fine of $${updated.fine.toFixed(2)}.` : '.'),
        );
        this.busyId.set(null);
        this.loadLendOptions();
        this.load();
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.busyId.set(null);
      },
    });
  }

  /** Members and in-stock books for the two dropdowns (first 100 of each is plenty for a demo library). */
  private loadLendOptions(): void {
    this.membersApi.list({ size: 100, sort: 'name,asc' }).subscribe({ next: (p) => this.members.set(p.content) });
    this.booksApi
      .list({ availableOnly: true, size: 100, sort: 'title,asc' })
      .subscribe({ next: (p) => this.availableBooks.set(p.content) });
  }
}
