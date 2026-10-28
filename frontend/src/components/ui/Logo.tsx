/** TsarevnaCache logo mark (crown over a cache bar), drawn in the theme accent colors. */
export function Logo({ className = 'h-7 w-7' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden>
      <rect width="64" height="64" rx="14" fill="#1C2128" />
      <path
        d="M14 42 L20 22 L28 34 L32 16 L36 34 L44 22 L50 42 Z"
        fill="none"
        stroke="#00E5FF"
        strokeWidth="3.5"
        strokeLinejoin="round"
      />
      <rect x="14" y="44" width="36" height="5" rx="2.5" fill="#9B51E0" />
      <circle cx="32" cy="14" r="3" fill="#2ECC71" />
    </svg>
  );
}
