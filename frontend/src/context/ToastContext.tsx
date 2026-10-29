import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { cn } from '../utils/formatters';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface ToastOptions {
  type?: ToastType;
  duration?: number;
  action?: ToastAction;
}

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
  action?: ToastAction;
}

interface ToastContextType {
  showToast: (message: string, typeOrOptions?: ToastType | ToastOptions) => void;
  removeToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback((message: string, typeOrOptions?: ToastType | ToastOptions) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`;
    const options: ToastOptions = typeof typeOrOptions === 'string'
      ? { type: typeOrOptions }
      : typeOrOptions || {};

    const type = options.type || 'info';
    const duration = options.duration ?? (options.action ? 6000 : 4000);
    const action = options.action;

    setToasts((prev) => [...prev, { id, message, type, action }]);

    if (duration > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    }
  }, []);

  return (
    <ToastContext.Provider value={{ showToast, removeToast }}>
      {children}
      {/* Toast container */}
      <div
        aria-live="polite"
        className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none"
        data-testid="toast-container"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.type === 'error' ? 'alert' : 'status'}
            data-testid={`toast-${toast.type}`}
            className={cn(
              'pointer-events-auto p-3.5 rounded-2xl border shadow-xl flex items-center justify-between gap-3 text-xs font-medium animate-in fade-in slide-in-from-bottom-2 duration-200 transition-all',
              toast.type === 'success' && 'bg-surface border-brand/40 text-foreground shadow-brand/5',
              toast.type === 'error' && 'bg-surface border-status-red/40 text-foreground shadow-status-red/5',
              toast.type === 'info' && 'bg-surface border-border text-foreground'
            )}
          >
            <div className="flex items-center gap-2.5 min-w-0">
              {toast.type === 'success' && <CheckCircle2 className="w-4 h-4 text-brand shrink-0" />}
              {toast.type === 'error' && <AlertTriangle className="w-4 h-4 text-status-red shrink-0" />}
              {toast.type === 'info' && <Info className="w-4 h-4 text-brand shrink-0" />}
              <span className="truncate">{toast.message}</span>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {toast.action && (
                <button
                  type="button"
                  onClick={() => {
                    toast.action?.onClick();
                    removeToast(toast.id);
                  }}
                  className="text-brand font-semibold hover:underline text-xs px-2.5 py-1 rounded-md bg-brand/10 hover:bg-brand/20 transition-colors cursor-pointer"
                  data-testid="toast-action-btn"
                >
                  {toast.action.label}
                </button>
              )}
              <button
                type="button"
                onClick={() => removeToast(toast.id)}
                className="text-foreground-muted hover:text-foreground cursor-pointer p-0.5 rounded focus-visible:outline-2 focus-visible:outline-brand"
                aria-label="Dismiss toast"
                data-testid="dismiss-toast-btn"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = (): ToastContextType => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
