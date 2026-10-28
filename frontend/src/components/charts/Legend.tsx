interface LegendItem {
  label: string;
  color: string;
  value?: string;
}

/** Inline legend: swatch (identity) + label and optional value in text colors. */
export function Legend({ items }: { items: LegendItem[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ background: item.color }} aria-hidden />
          <span className="text-gk-muted">{item.label}</span>
          {item.value && <span className="gk-mono text-gk-text">{item.value}</span>}
        </li>
      ))}
    </ul>
  );
}
