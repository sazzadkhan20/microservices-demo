import type { ReactNode } from "react";

type Props = {
  label: string;
  required?: boolean;
  hint?: ReactNode;
  error?: string;
  full?: boolean;
  children: ReactNode;
  className?: string;
};

/** Label + control + hint + inline error, matching the prototype `.field` block. */
export function FormField({ label, required, hint, error, full, children, className }: Props) {
  return (
    <label className={`field${full ? " full" : ""}${className ? ` ${className}` : ""}`}>
      <span>
        {label}
        {required ? <i className="req">*</i> : null}
      </span>
      {children}
      {hint ? <small>{hint}</small> : null}
      {error ? <span className="field-err">{error}</span> : null}
    </label>
  );
}
