import type { ComponentProps } from 'react';
import { ChevronDown } from 'lucide-react';

/**
 * shadcn/ui Native Select pattern. For one short local authorized-group list,
 * the browser's native select is the recommended accessible primitive.
 * The wrapper provides consistent design-system typography and affordance.
 */
export function NativeSelect({ className = '', children, ...props }: ComponentProps<'select'>) {
  return <div data-slot="native-select-wrapper" className="relative min-w-0">
    <select data-slot="native-select"
      className={`h-10 w-full min-w-0 appearance-none rounded-md border border-border bg-card px-3 pe-9 text-sm text-foreground shadow-xs outline-none focus-visible:border-[#6a5bff] focus-visible:ring-2 focus-visible:ring-[#6a5bff]/20 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      {...props}>{children}</select>
    <ChevronDown size={15} aria-hidden="true"
      className="pointer-events-none absolute end-3 top-1/2 -translate-y-1/2 text-muted-foreground"/>
  </div>;
}
