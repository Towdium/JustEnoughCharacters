"""Offline self-tests for the harness itself.

``jt selftest`` discovers ``selftest/test_*.py`` and runs them with unittest. No
test touches the network: the API clients are exercised through fabricated
payloads and stub http objects, and the download path is proven to reuse the
cache without a request.
"""

import os
import sys

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)
