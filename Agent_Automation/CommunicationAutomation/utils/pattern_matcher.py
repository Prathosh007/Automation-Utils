import re


def matches_pattern(text: str, pattern: str, case_sensitive: bool = False) -> bool:
    """Check if text matches a regex pattern."""
    if not pattern:
        return True
    flags = 0 if case_sensitive else re.IGNORECASE
    try:
        return bool(re.search(pattern, text, flags))
    except re.error:
        return pattern in text


def validate_value(actual: str, expected: str, validation_type: str = "contains",
                   case_sensitive: bool = False) -> tuple[bool, str]:
    """Validate actual value against expected using the specified validation type.

    Returns (success, message) tuple.
    """
    if not case_sensitive:
        actual_cmp = actual.lower() if actual else ""
        expected_cmp = expected.lower() if expected else ""
    else:
        actual_cmp = actual or ""
        expected_cmp = expected or ""

    # Truncate actual for display to keep messages readable
    actual_display = actual if len(actual or "") <= 100 else actual[:100] + "..."

    vtype = validation_type.lower()

    if vtype == "exact":
        ok = actual_cmp == expected_cmp
        msg = f"Expected (exact): '{expected}' | Got: '{actual_display}'"
    elif vtype == "contains":
        ok = expected_cmp in actual_cmp
        msg = f"Expected (contains): '{expected}' | Got: '{actual_display}'"
    elif vtype == "startswith":
        ok = actual_cmp.startswith(expected_cmp)
        msg = f"Expected (startsWith): '{expected}' | Got: '{actual_display}'"
    elif vtype == "endswith":
        ok = actual_cmp.endswith(expected_cmp)
        msg = f"Expected (endsWith): '{expected}' | Got: '{actual_display}'"
    elif vtype == "regex":
        flags = 0 if case_sensitive else re.IGNORECASE
        try:
            ok = bool(re.search(expected, actual or "", flags))
        except re.error as e:
            return False, f"Expected (regex): '{expected}' | Error: invalid regex - {e}"
        msg = f"Expected (regex): '{expected}' | Got: '{actual_display}'"
    elif vtype == "notempty":
        ok = bool(actual and actual.strip())
        msg = f"Expected: non-empty value | Got: '{actual_display}'"
    elif vtype == "isempty":
        ok = not actual or not actual.strip()
        msg = f"Expected: empty value | Got: '{actual_display}'"
    else:
        return False, f"Unknown validation type: '{validation_type}'"

    return ok, msg
