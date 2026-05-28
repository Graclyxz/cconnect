"""Auto-discovers tools from sibling modules and assembles the cconnect MCP server.
Drop a new .py file here exposing `tools = [<decorated_fn>, ...]` and it's picked
up on next backend start — no manual registration."""

import importlib
import pkgutil
from typing import Any


def build_cconnect_server() -> Any:
    from claude_agent_sdk import create_sdk_mcp_server

    collected: list = []
    for info in pkgutil.iter_modules(__path__):
        module = importlib.import_module(f"{__name__}.{info.name}")
        tools = getattr(module, "tools", None)
        if tools:
            collected.extend(tools)
    return create_sdk_mcp_server(name="cconnect", version="1.0.0", tools=collected)
