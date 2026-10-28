const INTERNAL_KEY = 'tsarevna.sidebar.showInternal';

/** Whether the sidebar lists Hazelcast internal objects (remembered per browser). */
export function readShowInternal(): boolean {
  try {
    return window.localStorage.getItem(INTERNAL_KEY) === 'true';
  } catch {
    return false;
  }
}

export function writeShowInternal(value: boolean): void {
  try {
    window.localStorage.setItem(INTERNAL_KEY, String(value));
  } catch {
    // storage may be unavailable (private mode)
  }
}
