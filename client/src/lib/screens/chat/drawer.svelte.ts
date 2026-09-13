import { navigation } from "$lib/app/navigation.svelte";

class Drawer {
  open = $state(false);

  readonly showing = $derived(this.open && navigation.chatActive);
}

export const drawer = new Drawer();
