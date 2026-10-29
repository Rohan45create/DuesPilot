import React, { useEffect, useState } from 'react';

export function usePrefersReducedMotion(): boolean {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const listener = (event: MediaQueryListEvent) => {
      setPrefersReducedMotion(event.matches);
    };
    mediaQuery.addEventListener('change', listener);
    return () => mediaQuery.removeEventListener('change', listener);
  }, []);

  return prefersReducedMotion;
}

export function useCountUp(target: number, durationMs: number = 800): number {
  const prefersReduced = usePrefersReducedMotion();
  const [current, setCurrent] = useState(() => (prefersReduced ? target : 0));

  useEffect(() => {
    if (prefersReduced || target === 0) {
      setCurrent(target);
      return;
    }

    let startTimestamp: number | null = null;
    let animationFrameId: number;

    const step = (timestamp: number) => {
      if (!startTimestamp) startTimestamp = timestamp;
      const progress = Math.min((timestamp - startTimestamp) / durationMs, 1);
      // ease-out cubic
      const easeProgress = 1 - Math.pow(1 - progress, 3);
      setCurrent(Math.round(target * easeProgress));

      if (progress < 1) {
        animationFrameId = requestAnimationFrame(step);
      } else {
        setCurrent(target);
      }
    };

    animationFrameId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(animationFrameId);
  }, [target, durationMs, prefersReduced]);

  return current;
}

export interface CountUpNumberProps {
  value: number;
  durationMs?: number;
  className?: string;
  prefix?: string;
  suffix?: string;
  formatter?: (val: number) => string;
}

export const CountUpNumber: React.FC<CountUpNumberProps> = ({
  value,
  durationMs = 800,
  className,
  prefix = '',
  suffix = '',
  formatter,
}) => {
  const animatedValue = useCountUp(value, durationMs);
  const display = formatter ? formatter(animatedValue) : animatedValue.toLocaleString();

  return (
    <span className={className} data-testid="count-up-number">
      {prefix}
      {display}
      {suffix}
    </span>
  );
};
