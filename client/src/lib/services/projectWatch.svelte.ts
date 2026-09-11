import { backend, type Profile } from "./backend.svelte";
import { ReconnectingSocket } from "./socket";

export class ProjectWatch {
  revision = $state(0);

  #socket: ReconnectingSocket;
  #projectKey = "";

  constructor(profile: () => Profile = () => backend.active) {
    this.#socket = new ReconnectingSocket(
      "/projects/ws",
      {
        onOpen: () => this.#sendWatch(),
        onMessage: (message) => this.#apply(message),
      },
      profile,
    );
  }

  connect() {
    this.#socket.connect();
  }

  close() {
    this.#socket.close();
  }

  watch(projectKey: string) {
    this.#projectKey = projectKey;
    this.#sendWatch();
  }

  refresh() {
    this.revision++;
  }

  #sendWatch() {
    if (this.#projectKey) this.#socket.send({ type: "watch", project_key: this.#projectKey });
  }

  #apply(message: Record<string, unknown>) {
    if (message.type !== "changed") return;
    if ((message.project_key ?? "") !== this.#projectKey) return;
    this.revision++;
  }
}
