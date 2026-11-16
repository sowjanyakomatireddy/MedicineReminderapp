import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { useId } from 'react';

/** Primary button: at least 56px tall for easy tapping. */
export function Button({ className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      className={`min-h-14 w-full rounded-2xl bg-teal px-5 text-lg font-semibold text-white hover:bg-teal-dark disabled:opacity-60 ${className}`}
      {...props}
    />
  );
}

export function TextField({
  label,
  hint,
  error,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const id = useId();
  const hintId = `${id}-hint`;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-medium">
        {label}
      </label>
      <input
        id={id}
        aria-describedby={hint || error ? hintId : undefined}
        aria-invalid={!!error}
        className="min-h-14 rounded-xl border-2 border-muted/40 bg-white px-4 text-lg focus:border-teal"
        {...props}
      />
      {(error || hint) && (
        <p id={hintId} className={error ? 'text-danger' : 'text-muted'}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

export function Alert({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-xl border-2 border-danger bg-white p-3 font-medium text-danger">
      {children}
    </p>
  );
}
