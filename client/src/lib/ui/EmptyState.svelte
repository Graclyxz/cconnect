<script lang="ts">
  import type { Snippet } from "svelte";
  import CenteredProgress from "./CenteredProgress.svelte";

  interface Props {
    text: string;
    loading?: boolean;
    class?: string;
    action?: Snippet;
  }

  const { text, loading = false, class: className = "", action }: Props = $props();
</script>

{#if loading}
  <CenteredProgress class={className} />
{:else}
  <div class="flex flex-col items-center justify-center gap-3 px-5 py-3.5 {className}">
    <p class="text-center text-body-md text-on-surface-variant">{text}</p>
    {@render action?.()}
  </div>
{/if}
