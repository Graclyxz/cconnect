"""The security key gating what belongs to the machine itself: shells and ignored files."""

import hmac

from core.config import PUBLIC_ACCESS_TOKEN, SECURITY_KEY

KEY_HEADER = "X-Security-Key"


def gated() -> bool:
    return PUBLIC_ACCESS_TOKEN is not None


def key_matches(candidate: str) -> bool:
    if not gated():
        return True
    return bool(SECURITY_KEY) and hmac.compare_digest(candidate or "", SECURITY_KEY)
