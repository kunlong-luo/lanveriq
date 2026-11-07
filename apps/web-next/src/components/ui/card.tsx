import type { ComponentProps } from 'react';

/**
 * shadcn/ui Card source-owned component convention.
 * Design-only semantic sections use native divs as intended by shadcn/ui.
 * No hand-written state, keyboard handling, focus traps or data transports.
 */
function Card({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card"
    className={`bg-card text-card-foreground flex flex-col gap-6 rounded-xl border border-border py-6 shadow-sm ${className}`}
    {...props} />;
}
function CardHeader({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-header"
    className={`@container/card-header grid auto-rows-min grid-rows-[auto_auto] items-start gap-1.5 px-6 has-[data-slot=card-action]:grid-cols-[1fr_auto] ${className}`}
    {...props} />;
}
function CardTitle({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-title"
    className={`font-semibold leading-none tracking-tight ${className}`} {...props} />;
}
function CardDescription({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-description"
    className={`text-muted-foreground text-sm ${className}`} {...props} />;
}
function CardAction({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-action"
    className={`col-start-2 row-span-2 row-start-1 self-start justify-self-end ${className}`}
    {...props} />;
}
function CardContent({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-content" className={`px-6 ${className}`} {...props} />;
}
function CardFooter({ className = '', ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-footer"
    className={`flex items-center px-6 ${className}`} {...props} />;
}
export { Card, CardHeader, CardTitle, CardDescription, CardAction, CardContent, CardFooter };
