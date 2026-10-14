import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { LoanService } from '../../core/api/loan.service';
import { Loan } from '../../core/models';
import { BookCoverComponent } from '../../shared/book-cover.component';
import { LoanStatusPipe, dueLabel } from '../../shared/loan-status.pipe';
import { errorMessage } from '../../shared/error-message';

/**
 * "My loans" (members only): what I have now, when it is due, and my borrowing history.
 *
 * Status, days left and fines are all computed by the SERVER for "today" and arrive in each Loan, so this
 * component only has to display them. Renew and Return call the API and replace the changed loan in place.
 */
@Component({
  selector: 'app-my-loans',
  standalone: true,
  imports: [RouterLink, DatePipe, CurrencyPipe, BookCoverComponent, LoanStatusPipe],
  templateUrl: './my-loans.component.html',
  styleUrl: './my-loans.component.scss',
})
export class MyLoansComponent implements OnInit {
  private readonly api = inject(LoanService);

  readonly loans = signal<Loan[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  /** Id of the loan with a request in flight, to disable its buttons. */
  readonly busyId = signal<number | null>(null);

  // Derived lists: recalculated automatically whenever `loans` changes.
  readonly current = computed(() => this.loans().filter((l) => l.status !== 'RETURNED'));
  readonly history = computed(() => this.loans().filter((l) => l.status === 'RETURNED'));
  readonly overdueCount = computed(() => this.current().filter((l) => l.status === 'OVERDUE').length);
  /** Fines on returned loans plus fines still growing on overdue ones. */
  readonly totalFines = computed(() => this.loans().reduce((sum, l) => sum + l.fine, 0));

  /** Exposed to the template. */
  readonly dueLabel = dueLabel;

  ngOnInit(): void {
    this.api.mine().subscribe({
      next: (loans) => {
        this.loans.set(loans);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  renew(loan: Loan): void {
    this.run(loan, this.api.renew(loan.id), (updated) => `Renewed “${updated.bookTitle}”. New due date: ${updated.dueDate}.`);
  }

  returnLoan(loan: Loan): void {
    this.run(loan, this.api.returnLoan(loan.id), (updated) =>
      updated.fine > 0
        ? `Returned “${updated.bookTitle}” ${-(loan.daysUntilDue ?? 0)} day(s) late. A fine of $${updated.fine.toFixed(2)} was recorded.`
        : `Returned “${updated.bookTitle}”. Thank you!`,
    );
  }

  /** Text for the disabled Renew button's tooltip. */
  renewHint(loan: Loan): string {
    if (loan.canRenew) return 'Extend the due date by 14 days';
    if (loan.status === 'OVERDUE') return 'Overdue loans cannot be renewed';
    return 'Already renewed once';
  }

  /** Shared plumbing for renew/return: busy flag, replace the loan in the list, success or error message. */
  private run(loan: Loan, request$: ReturnType<LoanService['renew']>, success: (l: Loan) => string): void {
    this.busyId.set(loan.id);
    this.error.set(null);
    this.notice.set(null);
    request$.subscribe({
      next: (updated) => {
        this.loans.update((list) => list.map((l) => (l.id === updated.id ? updated : l)));
        this.notice.set(success(updated));
        this.busyId.set(null);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.busyId.set(null);
      },
    });
  }
}
