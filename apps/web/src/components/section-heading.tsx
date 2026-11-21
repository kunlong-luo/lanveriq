export function SectionHeading({ eyebrow, title, description }: Readonly<{
  eyebrow: string; title: string; description: string;
}>) {
  return <div className="section-heading"><p className="eyebrow">{eyebrow}</p>
    <h1>{title}</h1><p className="section-description">{description}</p></div>;
}
