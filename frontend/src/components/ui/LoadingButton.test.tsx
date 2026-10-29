import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Button } from './Button';

describe('LoadingButton (Button with isLoading prop)', () => {
  it('renders standard button text and handles clicks when not loading', () => {
    const handleClick = vi.fn();
    render(
      <Button onClick={handleClick}>
        Submit Analysis
      </Button>
    );

    const btn = screen.getByRole('button', { name: 'Submit Analysis' });
    expect(btn).toBeInTheDocument();
    expect(btn).not.toBeDisabled();
    expect(screen.queryByTestId('button-spinner')).not.toBeInTheDocument();

    fireEvent.click(btn);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('renders spinner and loadingText, sets aria-busy, and disables button when isLoading is true', () => {
    const handleClick = vi.fn();
    render(
      <Button isLoading={true} loadingText="Saving draft..." onClick={handleClick}>
        Save
      </Button>
    );

    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByTestId('button-spinner')).toBeInTheDocument();
    expect(screen.getByText('Saving draft...')).toBeInTheDocument();

    // Double submit should be blocked because the button is disabled
    fireEvent.click(btn);
    expect(handleClick).not.toHaveBeenCalled();
  });

  it('falls back to children when loadingText is not specified', () => {
    render(
      <Button isLoading={true}>
        Confirm Action
      </Button>
    );

    expect(screen.getByTestId('button-spinner')).toBeInTheDocument();
    expect(screen.getByText('Confirm Action')).toBeInTheDocument();
  });

  it('applies interactive active-press and focus styling classes', () => {
    const { container } = render(
      <Button variant="primary">
        Interactive Button
      </Button>
    );

    const btn = container.querySelector('button');
    expect(btn).toHaveClass('active:scale-[0.98]');
    expect(btn).toHaveClass('cursor-pointer');
    expect(btn).toHaveClass('transition-all');
  });
});
