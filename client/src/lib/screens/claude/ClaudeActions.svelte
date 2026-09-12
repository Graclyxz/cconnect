<script lang="ts">
  import RotateCw from "@lucide/svelte/icons/rotate-cw";
  import { claudeRefresh } from "$lib/data/claudeRefresh.svelte";
  import { t } from "$lib/i18n/index.svelte";
  import { useShortcut } from "$lib/platform/useShortcut.svelte";
  import { paneActionClass } from "$lib/screens/chat/paneChrome";
  import { inPane } from "$lib/screens/chat/paneSurface";
  import TooltipIconButton from "$lib/ui/TooltipIconButton.svelte";

  const compact = inPane();

  useShortcut("window.refresh", () => void claudeRefresh.run());
</script>

<TooltipIconButton
  label={t("REFRESH")}
  class={paneActionClass(compact)}
  shortcut="window.refresh"
  onclick={() => void claudeRefresh.run()}
>
  <RotateCw />
</TooltipIconButton>
