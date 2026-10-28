/**
 * Chart colors and axis styling.
 *
 * The two data series colors are validated (OKLCH lightness band, chroma, CVD and normal-vision
 * separation, contrast) against the panel surface #1C2128. The neon UI cyan #00E5FF is too light
 * for filled marks on the dark surface, so data marks use the deeper cyan of the same hue.
 */
export const CHART_COLORS = {
  /** First series: owned memory, reads, near cache hits. */
  primary: '#0AA5C0',
  /** Second series: backup memory, writes, near cache misses. */
  secondary: '#9B51E0',
  surface: '#1C2128',
  grid: '#2D333B',
  axisText: '#8B949E',
  text: '#E6EDF3',
  cursor: 'rgba(255, 255, 255, 0.04)',
} as const;

export const AXIS_TICK = { fill: CHART_COLORS.axisText, fontSize: 11 } as const;
export const MONO_AXIS_TICK = {
  fill: CHART_COLORS.axisText,
  fontSize: 11,
  fontFamily: '"JetBrains Mono", monospace',
} as const;
