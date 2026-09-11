<script lang="ts">
  import FilePen from "@lucide/svelte/icons/file-pen";
  import type { DiffLine } from "$lib/data/chatModels";
  import DiffView from "$lib/ui/DiffView.svelte";
  import Collapsible from "./Collapsible.svelte";

  interface Props {
    path: string;
    diffLines: DiffLine[];
    labelOnly: boolean;
    expanded?: boolean | null;
    onToggle?: (() => void) | null;
  }

  const { path, diffLines, labelOnly, expanded = null, onToggle = null }: Props = $props();
</script>

<Collapsible
  label={path}
  icon={FilePen}
  labelOnly={labelOnly || !diffLines.length}
  {expanded}
  {onToggle}
  labelClass="text-accent"
>
  <DiffView {diffLines} class="rounded-sm bg-surface-variant py-1" />
</Collapsible>
