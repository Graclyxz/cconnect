<script lang="ts">
  import ArrowLeft from "@lucide/svelte/icons/arrow-left";
  import CircleUser from "@lucide/svelte/icons/circle-user";
  import Server from "@lucide/svelte/icons/server";
  import { navigation } from "$lib/app/navigation.svelte";
  import { useHighlight } from "$lib/app/useHighlight.svelte";
  import { isTouch } from "$lib/platform";
  import { useRefreshTick } from "$lib/platform/useRefreshTick.svelte";
  import { settings } from "$lib/data/settings.svelte";
  import { t } from "$lib/i18n/index.svelte";
  import { address, backend } from "$lib/services/backend.svelte";
  import { accountsStore } from "$lib/data/accountsStore.svelte";
  import { claudeRefresh } from "$lib/data/claudeRefresh.svelte";
  import { settingsApi } from "$lib/services/settingsApi";
  import { tabs } from "$lib/screens/chat/tabs.svelte";
  import AppTopBar from "$lib/ui/AppTopBar.svelte";
  import PullToRefresh from "$lib/ui/PullToRefresh.svelte";
  import SelectDialog from "$lib/ui/SelectDialog.svelte";
  import StatusDot from "$lib/ui/StatusDot.svelte";
  import TooltipIconButton from "$lib/ui/TooltipIconButton.svelte";
  import ClaudeDetail, { CLAUDE_KINDS, type ClaudeKind } from "./ClaudeDetail.svelte";
  import ClaudeActions from "./ClaudeActions.svelte";
  import ClaudeSections from "./ClaudeSections.svelte";

  let envOpen = $state(false);
  let accountOpen = $state(false);

  const highlight = useHighlight();
  const chat = $derived(tabs.state);
  const environment = $derived(backend.active);
  const offline = $derived(chat.link === "disconnected");
  const detail = $derived(
    (CLAUDE_KINDS as readonly string[]).includes(navigation.sub ?? "") ? (navigation.sub as ClaudeKind) : null,
  );
  const loggedAccounts = $derived(accountsStore.items.filter((account) => account.loggedIn));
  const tick = $derived(claudeRefresh.tick + (chat.connected ? 1 : 0));
  const refreshing = $derived(claudeRefresh.refreshing);

  const refresh = () => claudeRefresh.run();

  useRefreshTick(() => void refresh());

  $effect(() => {
    void tick;
    void backend.activeId;
    void accountsStore.load();
  });
</script>

{#if detail}
  <ClaudeDetail kind={detail} onClose={() => navigation.closeSub()} />
{:else}
  <div class="flex h-full flex-col">
    <AppTopBar title={t("CLAUDE")} subtitle={offline ? t("SERVER_UNAVAILABLE") : (environment?.name ?? null)}>
      {#snippet navigationIcon()}
        <TooltipIconButton label={t("BACK")} onclick={() => navigation.back()}>
          <ArrowLeft size={20} />
        </TooltipIconButton>
      {/snippet}
      {#snippet subtitleLeading()}
        {#if offline}
          <StatusDot class="bg-red" box={8} />
        {/if}
      {/snippet}
      {#snippet actions()}
        {#if loggedAccounts.length > 1}
          <TooltipIconButton label={t("ACCOUNT")} onclick={() => (accountOpen = true)}>
            <CircleUser size={20} />
          </TooltipIconButton>
        {/if}
        <TooltipIconButton
          label={t("ENVIRONMENT")}
          enabled={!settings.environmentLocked}
          onclick={() => (envOpen = true)}
        >
          <Server size={20} />
        </TooltipIconButton>
        {#if !isTouch}
          <ClaudeActions />
        {/if}
      {/snippet}
    </AppTopBar>

    <PullToRefresh {refreshing} onRefresh={() => void refresh()}>
      <div class="px-4 pb-4">
        <ClaudeSections
          {tick}
          flashCli={highlight.is("cli")}
          onOpen={(kind) => navigation.openSub(kind)}
          onAccountsChanged={() => void refresh()}
        />
      </div>
    </PullToRefresh>
  </div>
{/if}

{#if envOpen}
  <SelectDialog
    title={t("ENVIRONMENT")}
    options={backend.environments.map((profile) => ({
      value: profile.id,
      label: profile.name,
      subtitle: address(profile),
    }))}
    selected={chat.environmentId ?? ""}
    onSelect={(id) => chat.selectEnvironment(id)}
    onDismiss={() => (envOpen = false)}
  />
{/if}

{#if accountOpen}
  <SelectDialog
    title={t("ACCOUNT")}
    options={loggedAccounts.map((account) => ({ value: account.id, label: account.label }))}
    selected={accountsStore.defaultId}
    onSelect={(id) => {
      void settingsApi.update({ account: id }).then(() => void refresh());
    }}
    onDismiss={() => (accountOpen = false)}
  />
{/if}
