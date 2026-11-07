import type { ComponentProps } from 'react';

/**
 * shadcn/ui Table pattern: a design-system layer on semantic HTML.
 * Table semantics intentionally remain native; data operations must be
 * server-authorized and can later use TanStack Table for sorting/pagination.
 */
export function Table({ className = '', ...props }: ComponentProps<'table'>) {
  return <div data-slot="table-container" className="relative w-full overflow-x-auto">
    <table data-slot="table" className={`w-full caption-bottom text-sm ${className}`} {...props} />
  </div>;
}
export function TableHeader({ className = '', ...props }: ComponentProps<'thead'>) {
  return <thead data-slot="table-header" className={`[&_tr]:border-b ${className}`} {...props} />;
}
export function TableBody({ className = '', ...props }: ComponentProps<'tbody'>) {
  return <tbody data-slot="table-body" className={`[&_tr:last-child]:border-0 ${className}`} {...props} />;
}
export function TableFooter({ className = '', ...props }: ComponentProps<'tfoot'>) {
  return <tfoot data-slot="table-footer" className={`border-t bg-muted/50 font-medium [&>tr]:last:border-b-0 ${className}`} {...props} />;
}
export function TableRow({ className = '', ...props }: ComponentProps<'tr'>) {
  return <tr data-slot="table-row" className={`border-b transition-colors hover:bg-muted/50 ${className}`} {...props} />;
}
export function TableHead({ className = '', ...props }: ComponentProps<'th'>) {
  return <th data-slot="table-head" className={`h-10 px-3 text-left align-middle font-medium whitespace-nowrap ${className}`} {...props} />;
}
export function TableCell({ className = '', ...props }: ComponentProps<'td'>) {
  return <td data-slot="table-cell" className={`px-3 py-3 align-middle ${className}`} {...props} />;
}
export function TableCaption({ className = '', ...props }: ComponentProps<'caption'>) {
  return <caption data-slot="table-caption" className={`mt-4 text-sm text-muted-foreground ${className}`} {...props} />;
}
