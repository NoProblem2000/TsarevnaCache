interface SegmentedProps<T extends string> {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel: string;
}

/** Segmented control (radio group) used for mode switches. */
export function Segmented<T extends string>({ options, value, onChange, ariaLabel }: SegmentedProps<T>) {
  return (
    <div role="radiogroup" aria-label={ariaLabel} className="inline-flex rounded-md border border-gk-border bg-gk-bg p-0.5">
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(option.value)}
            className={`whitespace-nowrap rounded px-3 py-1 text-xs font-medium transition-colors ${
              active ? 'bg-gk-card text-gk-cyan shadow-[inset_0_0_0_1px_rgba(0,229,255,0.35)]' : 'text-gk-muted hover:text-gk-text'
            }`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
