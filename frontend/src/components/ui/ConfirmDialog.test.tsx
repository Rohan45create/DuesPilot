import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ConfirmDialog } from './ConfirmDialog';

describe('ConfirmDialog Component', () => {
  it('does not render anything when isOpen is false', () => {
    render(
      <ConfirmDialog
        isOpen={false}
        title="Delete item?"
        description="Are you sure you want to delete this record?"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('renders alertdialog with accessible title and description when isOpen is true', () => {
    render(
      <ConfirmDialog
        isOpen={true}
        title="Delete case DP-0142?"
        description="This will permanently delete the reconciliation record."
        confirmLabel="Yes, Delete"
        cancelLabel="Keep case"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByRole('heading', { name: 'Delete case DP-0142?' })).toBeInTheDocument();
    expect(
      screen.getByText('This will permanently delete the reconciliation record.')
    ).toBeInTheDocument();
  });

  it('calls onCancel when Cancel button or Close icon is clicked', () => {
    const handleCancel = vi.fn();
    render(
      <ConfirmDialog
        isOpen={true}
        title="Discard changes?"
        description="Unsaved edits will be lost."
        onConfirm={vi.fn()}
        onCancel={handleCancel}
      />
    );

    const cancelBtn = screen.getByTestId('confirm-dialog-cancel-btn');
    fireEvent.click(cancelBtn);
    expect(handleCancel).toHaveBeenCalledTimes(1);

    const closeIconBtn = screen.getByRole('button', { name: /close dialog/i });
    fireEvent.click(closeIconBtn);
    expect(handleCancel).toHaveBeenCalledTimes(2);
  });

  it('calls onConfirm when Confirm button is clicked', () => {
    const handleConfirm = vi.fn();
    render(
      <ConfirmDialog
        isOpen={true}
        title="Approve notice?"
        description="Notice will be marked for sending."
        onConfirm={handleConfirm}
        onCancel={vi.fn()}
      />
    );

    const confirmBtn = screen.getByTestId('confirm-dialog-confirm-btn');
    fireEvent.click(confirmBtn);
    expect(handleConfirm).toHaveBeenCalledTimes(1);
  });

  it('closes dialog when Escape key is pressed', () => {
    const handleCancel = vi.fn();
    render(
      <ConfirmDialog
        isOpen={true}
        title="Discard draft?"
        description="Changes will be lost."
        onConfirm={vi.fn()}
        onCancel={handleCancel}
      />
    );

    fireEvent.keyDown(window, { key: 'Escape', code: 'Escape' });
    expect(handleCancel).toHaveBeenCalledTimes(1);
  });

  it('shows loading state on confirm button and disables buttons when isLoading is true', () => {
    render(
      <ConfirmDialog
        isOpen={true}
        isLoading={true}
        title="Processing payment"
        description="Waiting for banking confirmation."
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );

    const confirmBtn = screen.getByTestId('confirm-dialog-confirm-btn');
    const cancelBtn = screen.getByTestId('confirm-dialog-cancel-btn');

    expect(confirmBtn).toBeDisabled();
    expect(cancelBtn).toBeDisabled();
    expect(confirmBtn).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByTestId('button-spinner')).toBeInTheDocument();
  });
});
