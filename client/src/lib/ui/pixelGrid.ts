const SNAPPED = [
  "--chat-gap-lg",
  "--chat-gap-sm",
  "--chat-line-dense",
  "--chat-line-code",
  "--chat-pad-xs",
  "--chat-pager-gap",
];

const LAYOUT_UNIT = 1 / 64;

let grid = 0;
const snapped = new Map<string, number>();

const measureGrid = (): number => {
  const ratio = typeof window === "undefined" ? 0 : window.devicePixelRatio;
  return ratio > 0 ? 1 / ratio : 1;
};

export const pixelGrid = (): number => grid || (grid = measureGrid());

const densityOf = (): number => 1 / pixelGrid();

const layoutUnit = (): number => LAYOUT_UNIT * pixelGrid();

const floorUnit = (value: number): number => Math.max(0, Math.floor(value / LAYOUT_UNIT) * LAYOUT_UNIT);

export const snapPx = (value: number): number => {
  const density = densityOf();
  return Math.round(value * density) / density;
};

export const ceilPx = (value: number): number => {
  const density = densityOf();
  return Math.ceil((value - layoutUnit()) * density) / density;
};

export const snappedToken = (name: string, fallback: number): number => snapped.get(name) ?? fallback;

export const gridHeight = (node: HTMLElement, onChange?: (value: number) => void) => {
  let notify = onChange;
  let pad = 0;
  const read = () => {
    const natural = node.getBoundingClientRect().height - pad;
    const next = floorUnit(ceilPx(natural) - natural);
    if (next !== pad) {
      pad = next;
      node.style.paddingBottom = pad ? `${pad}px` : "";
    }
    notify?.(natural + pad);
  };
  const observer = new ResizeObserver(read);
  observer.observe(node);
  read();
  return {
    update(next?: (value: number) => void) {
      notify = next;
      read();
    },
    destroy() {
      observer.disconnect();
    },
  };
};

export const refreshPixelGrid = (): number => {
  grid = measureGrid();
  const root = document.documentElement;
  const declared = getComputedStyle(root);
  root.style.setProperty("--px-grid", `${grid}px`);
  for (const name of SNAPPED) {
    const nominal = parseFloat(declared.getPropertyValue(name));
    if (!Number.isFinite(nominal)) continue;
    const value = snapPx(nominal);
    snapped.set(name, value);
    root.style.setProperty(`${name}-snap`, `${value}px`);
  }
  return grid;
};
