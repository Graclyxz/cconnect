interface AndroidSelectionBridge {
  setSelectable: (value: boolean) => void;
}

const bridge = (): AndroidSelectionBridge | undefined =>
  (window as unknown as { AndroidSelection?: AndroidSelectionBridge }).AndroidSelection;

const EDITABLE = "input, textarea, [contenteditable='true']";

export const reportSelectableTarget = (target: EventTarget | null) => {
  const android = bridge();
  if (!android) return;
  const node = target instanceof Element ? target : null;
  android.setSelectable(!!node?.closest(`.selectable, ${EDITABLE}`));
};
