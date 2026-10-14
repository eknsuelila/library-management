import { LoanStatusPipe, dueLabel } from './loan-status.pipe';

// Pipes and plain functions can be tested without any Angular TestBed setup.
describe('LoanStatusPipe & dueLabel', () => {
  const pipe = new LoanStatusPipe();

  it('turns enum values into readable labels', () => {
    expect(pipe.transform('OVERDUE')).toBe('Overdue');
    expect(pipe.transform('ACTIVE')).toBe('Active');
  });

  it('describes the time left or the delay', () => {
    expect(dueLabel({ status: 'ACTIVE', daysUntilDue: 0 })).toBe('Due today');
    expect(dueLabel({ status: 'ACTIVE', daysUntilDue: 1 })).toBe('1 day left');
    expect(dueLabel({ status: 'ACTIVE', daysUntilDue: 9 })).toBe('9 days left');
    expect(dueLabel({ status: 'OVERDUE', daysUntilDue: -3 })).toBe('3 days overdue');
    expect(dueLabel({ status: 'RETURNED', daysUntilDue: null })).toBe('Returned');
  });
});
