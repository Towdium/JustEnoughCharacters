"""Error types and the exit-code contract.

The contract mirrors the CloudLib uitest harness the parent agent asked us to
follow: ``0`` passed, ``1`` a check failed or a report is unusable (fail-closed),
``3`` the watchdog fired. ``2`` is the extra code this tool needs for the cases a
gradle-driven in-process harness never sees: bad CLI usage, a malformed env file,
a dependency that cannot be resolved, a missing external tool.
"""

from __future__ import annotations

EXIT_OK = 0
EXIT_FAIL = 1
EXIT_USAGE = 2
EXIT_WATCHDOG = 3

EXIT_CODE_MEANING = {
    EXIT_OK: "passed",
    EXIT_FAIL: "a check failed, or the run report is missing/corrupt (fail-closed)",
    EXIT_USAGE: "bad usage, bad configuration, an unresolvable dependency, or a missing external tool",
    EXIT_WATCHDOG: "the run exceeded its watchdog budget",
}


class JtError(Exception):
    """A condition we can explain to the user, reported without a traceback."""

    exit_code = EXIT_USAGE


class UsageError(JtError):
    """The command line itself is wrong."""

    exit_code = EXIT_USAGE


class ConfigError(JtError):
    """Configuration on disk is wrong or unusable."""

    exit_code = EXIT_USAGE


class EnvSchemaError(ConfigError):
    """An ``envs/*.json`` file does not satisfy the documented schema."""


class DependencyError(ConfigError):
    """A mod version could not be resolved to exactly one pinned file."""


class ToolMissingError(ConfigError):
    """An external program we must call (lsof, /usr/libexec/java_home) is absent."""


class SecretMissingError(ConfigError):
    """A required API key environment variable is unset.

    The message names the variable only; the value is never read into a message,
    never logged and never written to a cache file.
    """


class NetworkError(JtError):
    """An HTTP request failed after its retries."""

    exit_code = EXIT_USAGE


class HttpError(NetworkError):
    """A non-2xx HTTP response."""

    def __init__(self, message, *, status=None, url=None, body=None):
        super().__init__(message)
        self.status = status
        self.url = url
        self.body = body


class ReportError(JtError):
    """A run report is missing, unreadable or unusable — fail-closed."""

    exit_code = EXIT_FAIL


class WatchdogTimeout(JtError):
    """A run exceeded its budget."""

    exit_code = EXIT_WATCHDOG
