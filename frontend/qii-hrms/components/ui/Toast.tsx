"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

type ToastItem = { id: number; text: string; bad?: boolean };
type ToastApi = { show: (text: string, bad?: boolean) => void; error: (e: unknown) => void };

const ToastContext = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const seq = useRef(0);

  const show = useCallback((text: string, bad?: boolean) => {
    const id = ++seq.current;
    setItems((cur) => [...cur, { id, text, bad }]);
    setTimeout(() => setItems((cur) => cur.filter((t) => t.id !== id)), bad ? 6000 : 3500);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      show,
      error: (e) => show(e instanceof Error ? e.message : String(e), true),
    }),
    [show],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-root" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast${t.bad ? " bad" : ""}`}>
            {t.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <ToastProvider>");
  return ctx;
}
