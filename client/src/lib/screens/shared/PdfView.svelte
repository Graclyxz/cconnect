<script lang="ts">
  import { tick, untrack } from "svelte";
  import { authHeadersOf, backend } from "$lib/services/backend.svelte";
  import CenteredProgress from "$lib/ui/CenteredProgress.svelte";

  interface Props {
    url: string;
    onerror: () => void;
  }

  const { url, onerror }: Props = $props();

  const RENDER_SCALE = 2;
  const MIN_ZOOM = 1;
  const MAX_ZOOM = 5;
  const DOUBLE_TAP_ZOOM = 2.5;
  const WHEEL_STEP = 0.0015;
  const HALF = 2;

  let viewport = $state<HTMLDivElement | null>(null);
  let host = $state<HTMLDivElement | null>(null);
  let width = $state(0);
  let baseHeight = $state(0);
  let zoom = $state(1);
  let loading = $state(true);

  const points = new Map<number, { x: number; y: number }>();
  let pinchDistance = 0;
  let pinchZoom = 1;
  let dragX = 0;
  let dragY = 0;

  const clamp = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));

  const render = async (container: HTMLDivElement, available: number) => {
    const pdfjs = await import("pdfjs-dist");
    pdfjs.GlobalWorkerOptions.workerSrc = new URL("pdfjs-dist/build/pdf.worker.mjs", import.meta.url).href;

    const response = await fetch(url, { headers: authHeadersOf(backend.active) });
    if (!response.ok) throw new Error(String(response.status));
    const document_ = await pdfjs.getDocument({ data: await response.arrayBuffer() }).promise;

    container.replaceChildren();
    for (let number = 1; number <= document_.numPages; number++) {
      const page = await document_.getPage(number);
      const base = page.getViewport({ scale: 1 });
      const scale = (available / base.width) * RENDER_SCALE;
      const rendered = page.getViewport({ scale });
      const canvas = document.createElement("canvas");
      canvas.width = Math.floor(rendered.width);
      canvas.height = Math.floor(rendered.height);
      canvas.style.display = "block";
      canvas.style.width = "100%";
      canvas.style.height = "auto";
      container.appendChild(canvas);
      const context = canvas.getContext("2d");
      if (!context) continue;
      await page.render({ canvas, canvasContext: context, viewport: rendered }).promise;
    }
    baseHeight = container.offsetHeight;
  };

  const spread = () => {
    const [first, second] = [...points.values()];
    return Math.hypot(first.x - second.x, first.y - second.y);
  };

  const centre = () => {
    const [first, second] = [...points.values()];
    return { x: (first.x + second.x) / HALF, y: (first.y + second.y) / HALF };
  };

  const zoomAt = async (target: number, clientX: number, clientY: number) => {
    const box = viewport;
    if (!box) return;
    const next = clamp(target);
    if (next === zoom) return;
    const rect = box.getBoundingClientRect();
    const focusX = clientX - rect.left;
    const focusY = clientY - rect.top;
    const ratio = next / zoom;
    const left = (box.scrollLeft + focusX) * ratio - focusX;
    const top = (box.scrollTop + focusY) * ratio - focusY;
    zoom = next;
    await tick();
    box.scrollLeft = left;
    box.scrollTop = top;
  };

  const onPointerDown = (event: PointerEvent) => {
    points.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (points.size === 2) {
      pinchDistance = spread();
      pinchZoom = zoom;
      return;
    }
    if (event.pointerType !== "touch" || zoom <= MIN_ZOOM) return;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    dragX = event.clientX;
    dragY = event.clientY;
  };

  const onPointerMove = (event: PointerEvent) => {
    if (!points.has(event.pointerId)) return;
    points.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (points.size >= 2) {
      if (pinchDistance <= 0) return;
      event.preventDefault();
      const focus = centre();
      void zoomAt((pinchZoom * spread()) / pinchDistance, focus.x, focus.y);
      return;
    }
    if (event.pointerType !== "touch" || zoom <= MIN_ZOOM) return;
    event.preventDefault();
    const box = viewport;
    if (box) {
      box.scrollLeft -= event.clientX - dragX;
      box.scrollTop -= event.clientY - dragY;
    }
    dragX = event.clientX;
    dragY = event.clientY;
  };

  const onPointerUp = (event: PointerEvent) => {
    points.delete(event.pointerId);
    if (points.size < 2) pinchDistance = 0;
  };

  const onWheel = (event: WheelEvent) => {
    if (!event.ctrlKey) return;
    event.preventDefault();
    void zoomAt(zoom - event.deltaY * WHEEL_STEP * zoom, event.clientX, event.clientY);
  };

  const onDoubleClick = (event: MouseEvent) => {
    void zoomAt(zoom > MIN_ZOOM ? MIN_ZOOM : DOUBLE_TAP_ZOOM, event.clientX, event.clientY);
  };

  $effect(() => {
    const container = host;
    const available = width;
    if (!container || available <= 0 || untrack(() => zoom) > MIN_ZOOM) return;
    let cancelled = false;
    loading = true;
    render(container, available)
      .then(() => {
        if (!cancelled) loading = false;
      })
      .catch(() => {
        if (!cancelled) onerror();
      });
    return () => (cancelled = true);
  });
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={viewport}
  bind:clientWidth={width}
  class="scrollbar-thin relative min-h-0 flex-1 overflow-auto overscroll-contain bg-neutral-800 {zoom >
  MIN_ZOOM
    ? 'touch-none'
    : 'touch-pan-y'}"
  onpointerdown={onPointerDown}
  onpointermove={onPointerMove}
  onpointerup={onPointerUp}
  onpointercancel={onPointerUp}
  onwheel={onWheel}
  ondblclick={onDoubleClick}
>
  <div class="relative" style="width: {width * zoom}px; height: {baseHeight * zoom}px">
    <div
      bind:this={host}
      class="absolute left-0 top-0 flex flex-col gap-2"
      style="width: {width}px; transform: scale({zoom}); transform-origin: 0 0"
    ></div>
  </div>
  {#if loading}
    <CenteredProgress class="sticky inset-0 h-full" />
  {/if}
</div>
