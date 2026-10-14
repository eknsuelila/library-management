import { Pipe, PipeTransform } from '@angular/core';
import { Loan, LoanStatus } from '../core/models';

/** Human-readable labels for the LoanStatus enum values. */
export const LOAN_STATUS_LABELS: Record<LoanStatus, string> = {
  ACTIVE: 'Active',
  OVERDUE: 'Overdue',
  RETURNED: 'Returned',
};

/**
 * A pipe transforms a value inside a template:  {{ loan.status | loanStatus }}  ->  "Overdue".
 * Pipes are "pure" by default, so Angular only re-runs them when the input value changes.
 */
@Pipe({ name: 'loanStatus', standalone: true })
export class LoanStatusPipe implements PipeTransform {
  transform(status: LoanStatus): string {
    return LOAN_STATUS_LABELS[status] ?? status;
  }
}

/**
 * Friendly due-date text for an open loan: "Due today", "3 days left", "2 days overdue".
 * A plain function (not a pipe) so it's trivial to unit-test and reuse in several components.
 */
export function dueLabel(loan: Pick<Loan, 'daysUntilDue' | 'status'>): string {
  const days = loan.daysUntilDue;
  if (loan.status === 'RETURNED' || days === null) return 'Returned';
  if (days === 0) return 'Due today';
  if (days > 0) return `${days} day${days === 1 ? '' : 's'} left`;
  const late = -days;
  return `${late} day${late === 1 ? '' : 's'} overdue`;
}
