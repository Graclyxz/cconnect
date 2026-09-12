const MIN_REFRESH_MS = 600;

class ClaudeRefresh {
  tick = $state(0);
  refreshing = $state(false);

  async run() {
    if (this.refreshing) return;
    this.refreshing = true;
    this.tick++;
    await new Promise((done) => setTimeout(done, MIN_REFRESH_MS));
    this.refreshing = false;
  }
}

export const claudeRefresh = new ClaudeRefresh();
