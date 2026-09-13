export interface NativeInsets {
  top: number;
  bottom: number;
  left: number;
  right: number;
  keyboard: number;
}

interface AndroidInsetsBridge {
  get: () => string;
}

const bridge = (): AndroidInsetsBridge | undefined =>
  (window as unknown as { AndroidInsets?: AndroidInsetsBridge }).AndroidInsets;

const parse = (raw: string): NativeInsets | null => {
  try {
    const value = JSON.parse(raw) as NativeInsets;
    return Number.isFinite(value.top) ? value : null;
  } catch {
    return null;
  }
};

export const watchAndroidInsets = (apply: (insets: NativeInsets) => void): boolean => {
  const android = bridge();
  if (!android) return false;
  const initial = parse(android.get());
  if (initial) apply(initial);
  (window as unknown as { __cconnectInsets?: (insets: NativeInsets) => void }).__cconnectInsets = apply;
  return true;
};
