<script lang="ts">
  interface Props {
    text: string;
    failure?: boolean;
    class?: string;
  }

  const { text, failure = false, class: className = "" }: Props = $props();
</script>

<p
  class="selectable rounded-md border-2 px-2.5 py-2 font-mono text-body-sm wrap-anywhere whitespace-pre-wrap {failure
    ? 'border-red text-red'
    : 'border-outline-variant text-on-surface'} {className}"
>{text}</p>
