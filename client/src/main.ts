import { mount } from "svelte";
import { polyfillFieldSizing } from "$lib/platform/fieldSizing";
import { trackPressFeedback } from "$lib/platform/pressFeedback";
import { SECURE_KEYS, secureStore } from "$lib/platform/secureStorage";
import "./app.css";

const SELECTABLE = "input, textarea, [contenteditable], .selectable";
const NATIVE_MENU = `${SELECTABLE}, [data-native-menu]`;

const matches = (target: EventTarget | null, selector: string) =>
  target instanceof Element && target.closest(selector) !== null;

const selectableRoot = (node: Node | null) => {
  const host = node instanceof Element ? node : node?.parentElement;
  return host?.closest(SELECTABLE) ?? null;
};

const focusedRegion = () =>
  selectableRoot(document.activeElement) ??
  [...document.querySelectorAll(".selectable")].find((node) => !node.closest("[data-unfocused]")) ??
  null;

document.addEventListener("contextmenu", (event) => {
  if (!matches(event.target, NATIVE_MENU)) event.preventDefault();
});

document.addEventListener("selectstart", (event) => {
  if (event.target !== document.body) return;
  event.preventDefault();
  const region = focusedRegion();
  if (!region) return;
  const range = document.createRange();
  range.selectNodeContents(region);
  const selection = document.getSelection();
  selection?.removeAllRanges();
  selection?.addRange(range);
});

document.addEventListener("selectionchange", () => {
  const selection = document.getSelection();
  if (!selection || selection.isCollapsed) return;
  if (!selectableRoot(selection.anchorNode)) selection.removeAllRanges();
});

polyfillFieldSizing();
trackPressFeedback();

await secureStore.load(SECURE_KEYS);
const { default: App } = await import("./App.svelte");

export default mount(App, { target: document.getElementById("app")! });
