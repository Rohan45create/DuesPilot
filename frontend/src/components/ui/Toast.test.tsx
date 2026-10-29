import React from 'react';
import { render, screen, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ToastProvider, useToast } from '../../context/ToastContext';

const TestComponent: React.FC<{
  onTrigger?: (showToast: ReturnType<typeof useToast>['showToast']) => void;
}> = ({ onTrigger }) => {
  const { showToast } = useToast();
  return (
    <div>
      <button
        type="button"
        data-testid="trigger-success-btn"
        onClick={() => showToast('Action succeeded', 'success')}
      >
        Success
      </button>
      <button
        type="button"
        data-testid="trigger-error-btn"
        onClick={() => showToast('Something failed', 'error')}
      >
        Error
      </button>
      <button
        type="button"
        data-testid="trigger-action-btn"
        onClick={() =>
          showToast('Draft approved', {
            type: 'info',
            duration: 3000,
            action: {
              label: 'Undo',
              onClick: () => onTrigger?.(showToast),
            },
          })
        }
      >
        With Action
      </button>
    </div>
  );
};

describe('Toast Component & Context', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('renders success toast with role status and auto-dismisses after timeout', () => {
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );

    const triggerBtn = screen.getByTestId('trigger-success-btn');
    act(() => {
      triggerBtn.click();
    });

    const toast = screen.getByTestId('toast-success');
    expect(toast).toBeInTheDocument();
    expect(toast).toHaveAttribute('role', 'status');
    expect(toast).toHaveTextContent('Action succeeded');

    // Auto dismiss after 4000ms
    act(() => {
      vi.advanceTimersByTime(4000);
    });

    expect(screen.queryByTestId('toast-success')).not.toBeInTheDocument();
  });

  it('renders error toast with role alert', () => {
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );

    const triggerBtn = screen.getByTestId('trigger-error-btn');
    act(() => {
      triggerBtn.click();
    });

    const toast = screen.getByTestId('toast-error');
    expect(toast).toBeInTheDocument();
    expect(toast).toHaveAttribute('role', 'alert');
    expect(toast).toHaveTextContent('Something failed');
  });

  it('renders action button (Undo) and fires callback when clicked', () => {
    const handleUndo = vi.fn();
    render(
      <ToastProvider>
        <TestComponent onTrigger={handleUndo} />
      </ToastProvider>
    );

    const triggerBtn = screen.getByTestId('trigger-action-btn');
    act(() => {
      triggerBtn.click();
    });

    const actionBtn = screen.getByTestId('toast-action-btn');
    expect(actionBtn).toBeInTheDocument();
    expect(actionBtn).toHaveTextContent('Undo');

    act(() => {
      actionBtn.click();
    });

    expect(handleUndo).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId('toast-action-btn')).not.toBeInTheDocument();
  });

  it('dismisses toast immediately when dismiss button is clicked', () => {
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );

    act(() => {
      screen.getByTestId('trigger-success-btn').click();
    });

    expect(screen.getByTestId('toast-success')).toBeInTheDocument();
    const dismissBtn = screen.getByTestId('dismiss-toast-btn');

    act(() => {
      dismissBtn.click();
    });

    expect(screen.queryByTestId('toast-success')).not.toBeInTheDocument();
  });
});
