type Handler = () => void;

const stack: Handler[] = [];

let consuming = false;

const depth = () => (window.history.state as { dismiss?: number } | null)?.dismiss ?? 0;

export const pushDismiss = (handler: Handler) => {
  stack.push(handler);
  window.history.pushState({ dismiss: stack.length }, "", window.location.href);
  return () => {
    const index = stack.lastIndexOf(handler);
    if (index < 0) return;
    stack.splice(index, 1);
    if (depth() <= stack.length) return;
    consuming = true;
    window.history.back();
  };
};

export const dismissTop = () => {
  const handler = stack.at(-1);
  if (!handler) return false;
  handler();
  return true;
};

export const consumingDismiss = () => {
  if (!consuming) return false;
  consuming = false;
  return true;
};

export const dismissOpen = () => stack.length > 0;
