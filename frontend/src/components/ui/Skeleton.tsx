import React from 'react';
import { cn } from '../../utils/formatters';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className, ...props }) => {
  return (
    <div
      aria-hidden="true"
      className={cn('skeleton-shimmer rounded-lg bg-surface-muted/60', className)}
      {...props}
    />
  );
};

export interface CardSkeletonProps {
  className?: string;
  count?: number;
}

export const CardSkeleton: React.FC<CardSkeletonProps> = ({ className, count = 1 }) => {
  return (
    <>
      {Array.from({ length: count }).map((_, idx) => (
        <div
          key={idx}
          data-testid="card-skeleton"
          className={cn(
            'p-5 rounded-2xl border border-border bg-surface flex flex-col gap-3',
            className
          )}
        >
          <div className="flex items-center justify-between">
            <Skeleton className="h-4 w-28" />
            <Skeleton className="h-8 w-8 rounded-full" />
          </div>
          <Skeleton className="h-7 w-36 mt-1" />
          <Skeleton className="h-3.5 w-20 mt-1" />
        </div>
      ))}
    </>
  );
};

export interface TableSkeletonProps {
  rows?: number;
  columns?: number;
  className?: string;
}

export const TableSkeleton: React.FC<TableSkeletonProps> = ({
  rows = 5,
  columns = 5,
  className,
}) => {
  return (
    <div data-testid="table-skeleton" className={cn('w-full space-y-3', className)}>
      {/* Table Header */}
      <div className="flex items-center gap-4 px-4 py-3 border-b border-border bg-surface-muted/30 rounded-t-xl">
        {Array.from({ length: columns }).map((_, idx) => (
          <Skeleton
            key={`head-${idx}`}
            className={cn('h-4', idx === 0 ? 'w-24' : 'flex-1')}
          />
        ))}
      </div>
      {/* Table Rows */}
      <div className="space-y-2">
        {Array.from({ length: rows }).map((_, rIdx) => (
          <div
            key={`row-${rIdx}`}
            className="flex items-center gap-4 px-4 py-3.5 border border-border/50 rounded-xl bg-surface/50"
          >
            {Array.from({ length: columns }).map((_, cIdx) => (
              <Skeleton
                key={`cell-${rIdx}-${cIdx}`}
                className={cn('h-3.5', cIdx === 0 ? 'w-28' : 'flex-1')}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
};

export interface ChartSkeletonProps {
  className?: string;
  height?: string;
}

export const ChartSkeleton: React.FC<ChartSkeletonProps> = ({
  className,
  height = 'h-64',
}) => {
  return (
    <div
      data-testid="chart-skeleton"
      className={cn(
        'w-full p-6 rounded-2xl border border-border bg-surface flex flex-col justify-between gap-6',
        height,
        className
      )}
    >
      <div className="flex items-center justify-between">
        <Skeleton className="h-5 w-40" />
        <div className="flex gap-2">
          <Skeleton className="h-4 w-14 rounded-full" />
          <Skeleton className="h-4 w-14 rounded-full" />
        </div>
      </div>
      <div className="flex items-end justify-between gap-3 flex-1 pt-6 pb-2">
        {[40, 75, 30, 90, 60, 45, 80, 50, 95, 65, 35, 70].map((val, idx) => (
          <div key={idx} className="flex-1 flex flex-col items-center gap-2 h-full justify-end">
            <Skeleton
              className="w-full rounded-t-md"
              style={{ height: `${val}%` }}
            />
            <Skeleton className="h-2 w-full max-w-[24px]" />
          </div>
        ))}
      </div>
    </div>
  );
};
