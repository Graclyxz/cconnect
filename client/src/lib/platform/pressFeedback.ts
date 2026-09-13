import { scrollableUnder } from "$lib/ui/scrollbar";
import { isTouch } from "./index";

const PRESSABLE = "button, a, [role='button'], [role='menuitem'], [role='menuitemradio'], [role='tab']";
const DISABLED = ":disabled, [data-disabled], [aria-disabled='true']";
const SURFACE = "[data-press]";
const BOXLESS = ["inline", "contents"];
const TAP_TIMEOUT_MS = 100;
const HOLD_MS = 225;
const FADE_MS = 150;
const TOUCH_SLOP = 8;

export const trackPressFeedback = () => {
  if (!isTouch) return;

  let candidate: HTMLElement | null = null;
  let originX = 0;
  let originY = 0;
  let shownAt = 0;
  let showTimer: ReturnType<typeof setTimeout> | null = null;

  const erase = (target: HTMLElement) => {
    delete target.dataset.pressed;
    for (const name of ["x", "y", "reach"]) {
      target.style.removeProperty(`--press-${name}`);
    }
  };

  const fadeOut = (target: HTMLElement) => {
    target.dataset.pressed = "off";
    setTimeout(() => {
      if (target.dataset.pressed === "off") erase(target);
    }, FADE_MS);
  };

  const show = (target: HTMLElement) => {
    shownAt = performance.now();
    target.dataset.pressed = "on";
  };

  const release = (target: HTMLElement) => {
    const held = performance.now() - shownAt;
    if (held >= HOLD_MS) fadeOut(target);
    else setTimeout(() => fadeOut(target), HOLD_MS - held);
  };

  const take = () => {
    const target = candidate;
    candidate = null;
    if (showTimer !== null) clearTimeout(showTimer);
    showTimer = null;
    return target;
  };

  const abandon = () => {
    const target = take();
    if (!target) return;
    if (target.dataset.pressed === "on") release(target);
    else erase(target);
  };

  const radiusOf = (node: Element) => parseFloat(getComputedStyle(node).borderTopLeftRadius) || 0;

  const soleShape = (pressable: HTMLElement) => {
    const child = pressable.children.length === 1 ? (pressable.firstElementChild as HTMLElement) : null;
    return child && radiusOf(child) > 0 ? child : null;
  };

  const shapeOf = (from: Element, pressable: HTMLElement) => {
    let node: Element | null = from;
    let rounded: HTMLElement | null = null;
    while (node) {
      if (radiusOf(node) > 0) rounded = node as HTMLElement;
      if (node === pressable) break;
      node = node.parentElement;
    }
    return (
      rounded ?? soleShape(pressable) ?? pressable.parentElement?.closest<HTMLElement>(SURFACE) ?? pressable
    );
  };

  const paint = (target: HTMLElement, clientX: number, clientY: number) => {
    const box = target.getBoundingClientRect();
    const x = clientX - box.left;
    const y = clientY - box.top;
    const reach = Math.hypot(Math.max(x, box.width - x), Math.max(y, box.height - y));
    target.style.setProperty("--press-x", `${x}px`);
    target.style.setProperty("--press-y", `${y}px`);
    target.style.setProperty("--press-reach", `${reach}px`);
  };

  document.addEventListener(
    "pointerdown",
    (event) => {
      abandon();
      const from = event.target instanceof Element ? event.target : null;
      const pressable = from?.closest<HTMLElement>(PRESSABLE) ?? null;
      if (!from || !pressable || pressable.matches(DISABLED)) return;
      const target = shapeOf(from, pressable);
      if (BOXLESS.includes(getComputedStyle(target).display)) return;
      if (target.dataset.pressed) {
        erase(target);
        void target.offsetWidth;
      }
      paint(target, event.clientX, event.clientY);
      candidate = target;
      originX = event.clientX;
      originY = event.clientY;
      if (scrollableUnder(event.clientX, event.clientY)) {
        showTimer = setTimeout(() => show(target), TAP_TIMEOUT_MS);
      } else {
        show(target);
      }
    },
    true,
  );

  document.addEventListener(
    "pointermove",
    (event) => {
      if (!candidate) return;
      const slipped =
        Math.abs(event.clientX - originX) > TOUCH_SLOP || Math.abs(event.clientY - originY) > TOUCH_SLOP;
      if (!slipped) return;
      const target = take();
      if (!target) return;
      if (target.dataset.pressed === "on") fadeOut(target);
      else erase(target);
    },
    { capture: true, passive: true },
  );

  document.addEventListener(
    "pointerup",
    () => {
      const target = take();
      if (!target) return;
      if (target.dataset.pressed !== "on") show(target);
      release(target);
    },
    true,
  );

  document.addEventListener("pointercancel", abandon, true);
  document.addEventListener("scroll", abandon, true);
};
