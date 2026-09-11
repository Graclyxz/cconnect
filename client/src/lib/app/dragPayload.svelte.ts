import { markDropTarget } from "$lib/ui/fileDrop";

export interface SharedFile {
  path: string;
  name: string;
  size: number;
}

export type DragPayload = { kind: "shared"; files: SharedFile[] };

export interface DropZone {
  accepts: (payload: DragPayload) => boolean;
  drop: (payload: DragPayload) => void;
}

class DragTransfer {
  payload = $state<DragPayload | null>(null);
  over = $state<HTMLElement | null>(null);

  #zones = new Map<HTMLElement, DropZone>();

  #hover(node: HTMLElement | null) {
    this.over = node;
    markDropTarget(node);
  }

  add(node: HTMLElement, zone: DropZone) {
    this.#zones.set(node, zone);
  }

  remove(node: HTMLElement) {
    this.#zones.delete(node);
    if (this.over === node) this.#hover(null);
  }

  begin(payload: DragPayload) {
    this.payload = payload;
    this.#hover(null);
  }

  track(x: number, y: number) {
    const payload = this.payload;
    if (!payload) return;
    const node = document.elementFromPoint(x, y)?.closest<HTMLElement>("[data-drop-zone]") ?? null;
    const zone = node ? this.#zones.get(node) : undefined;
    this.#hover(zone?.accepts(payload) ? node : null);
  }

  release(): boolean {
    const payload = this.payload;
    const zone = this.over ? this.#zones.get(this.over) : undefined;
    this.payload = null;
    this.#hover(null);
    if (!payload || !zone) return false;
    zone.drop(payload);
    return true;
  }
}

export const dragTransfer = new DragTransfer();

export const dropZone = (node: HTMLElement, zone: DropZone) => {
  node.dataset.dropZone = "";
  dragTransfer.add(node, zone);
  return {
    destroy: () => dragTransfer.remove(node),
  };
};
