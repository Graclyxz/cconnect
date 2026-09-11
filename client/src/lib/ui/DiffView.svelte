<script lang="ts">
  import type { DiffLine } from "$lib/data/chatModels";
  import { hscrollbar } from "./scrollbar";

  interface Props {
    diffLines: DiffLine[];
    class?: string;
  }

  const { diffLines, class: className = "" }: Props = $props();

  const LINE_CLASS: Record<DiffLine["kind"], string> = {
    header: "text-gray",
    hunk: "text-blue bg-blue-bg",
    add: "text-green bg-green-bg",
    del: "text-red bg-red-bg",
    ctx: "text-on-surface-variant",
  };

  const PREFIX: Record<DiffLine["kind"], string> = {
    header: "",
    hunk: "",
    add: "+",
    del: "-",
    ctx: " ",
  };
</script>

<div
  use:hscrollbar={{ touchIndicator: true }}
  class="no-scrollbar w-full overflow-x-auto font-mono text-body-sm leading-[18px] {className}"
>
  {#each diffLines as line, index (index)}
    <div class="w-max px-2.5 py-px whitespace-pre {LINE_CLASS[line.kind]}">{!line.text &&
      !PREFIX[line.kind]
        ? " "
        : `${PREFIX[line.kind]}${line.text}`}</div>
  {/each}
</div>
