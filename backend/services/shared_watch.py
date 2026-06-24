"""Live updates for the shared folder over WebSocket.

A single recursive watcher (``watchdog``, polling fallback) over the shared directory.
Each connection subscribes to one relative directory; on any change the watcher re-lists
every subscriber's directory and pushes the fresh snapshot, so the file explorer stays in
sync without a manual refresh.
"""

import asyncio
import threading
from pathlib import Path
from typing import Optional

from loguru import logger

from core.config import SHARED_DIR
from services import shared as shared_service

_DEBOUNCE_SECONDS = 0.4
_POLL_SECONDS = 2.0


class SharedWatchHub:
    def __init__(self):
        self._subscribers: dict[asyncio.Queue, str] = {}
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._lock = threading.Lock()
        self._pending = threading.Event()
        self._stop = threading.Event()
        self._observer = None
        self._started = False

    async def start(self):
        if self._started:
            return
        self._started = True
        self._loop = asyncio.get_running_loop()
        self._begin_watching()

    def stop(self):
        self._stop.set()
        self._pending.set()
        if self._observer is not None:
            try:
                self._observer.stop()
            except Exception:
                pass

    def subscribe(self) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue()
        with self._lock:
            self._subscribers[q] = ""
        return q

    def unsubscribe(self, q: asyncio.Queue):
        with self._lock:
            self._subscribers.pop(q, None)

    def set_path(self, q: asyncio.Queue, path: str):
        with self._lock:
            if q not in self._subscribers:
                return
            self._subscribers[q] = path
        q.put_nowait(self._snapshot(path))

    def _snapshot(self, path: str) -> dict:
        try:
            entries = shared_service.list_entries(path)
        except Exception:
            entries = []
        return {"type": "snapshot", "path": path, "entries": entries}

    def _begin_watching(self):
        base = SHARED_DIR
        handler = None
        if Path(base).is_dir():
            try:
                from watchdog.events import FileSystemEventHandler
                from watchdog.observers import Observer

                hub = self

                class _Handler(FileSystemEventHandler):
                    def on_any_event(self, event):
                        hub._pending.set()

                observer = Observer()
                observer.schedule(_Handler(), base, recursive=True)
                observer.daemon = True
                observer.start()
                self._observer = observer
                handler = "watchdog"
            except Exception as exc:
                logger.info(f"shared_watch: watchdog unavailable ({exc}); falling back to polling")
        if handler == "watchdog":
            threading.Thread(target=self._debounce_loop, name="shared-watch", daemon=True).start()
        else:
            threading.Thread(target=self._poll_loop, name="shared-poll", daemon=True).start()

    def _debounce_loop(self):
        while not self._stop.is_set():
            self._pending.wait()
            if self._stop.is_set():
                return
            self._stop.wait(_DEBOUNCE_SECONDS)
            self._pending.clear()
            self._broadcast()

    def _poll_loop(self):
        while not self._stop.is_set():
            self._stop.wait(_POLL_SECONDS)
            if self._stop.is_set():
                return
            self._broadcast()

    def _broadcast(self):
        loop = self._loop
        if loop is None:
            return
        with self._lock:
            items = list(self._subscribers.items())
        for q, path in items:
            snapshot = self._snapshot(path)
            loop.call_soon_threadsafe(q.put_nowait, snapshot)


hub = SharedWatchHub()
