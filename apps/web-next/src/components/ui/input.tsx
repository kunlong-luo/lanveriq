import type { ComponentProps } from 'react';

/**
 * shadcn/ui-style Input: a styled, accessible native control.
 * Native HTML semantics are deliberate; no bespoke keyboard handlers.
 */
export function Input({ className = '', type = 'text', ...props }: ComponentProps<'input'>) {
  return <input type={type} data-slot="input"
    className={`flex h-10 w-full min-w-0 rounded-md border border-border bg-transparent px-3 py-2 text-sm text-foreground shadow-xs transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-[#6a5bff] focus-visible:ring-2 focus-visible:ring-[#6a5bff]/20 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
    {...props} />;
}
