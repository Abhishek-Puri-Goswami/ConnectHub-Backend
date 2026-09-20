#!/usr/bin/env python
"""
Compares the (currently inactive) config repo with what the services really use.

config-server is not part of the boot, so nothing reads ../connecthub-config. The danger is that it *looks*
authoritative: if it is ever reactivated, its values override each service's own application.yml. So every
value in it must be either identical to the service's, or a documented, deliberate difference.

    python check-config-drift.py [path-to-config-repo]     (default: ..\\connecthub-config)

Needs PyYAML (pip install pyyaml). Exit code 1 when there is drift, so it can be part of the regression run.
"""
import glob
import os
import sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is required: pip install pyyaml")

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "connecthub-config"))

# Keys that legitimately exist only in the config repo (each service has them via other means).
ONLY_IN_CONFIG_OK = {"eureka.instance.appname"}
# Keys whose value is deliberately different. payment-service keeps developer defaults for its test-mode
# Razorpay keys in its own file; the config repo must hold no secrets at all.
DIFFERENT_OK = {
    "payment-service": {"razorpay.key-id", "razorpay.key-secret", "razorpay.webhook-secret"},
}


def norm(v):
    """'${VAR:default}' and a literal 'default' behave the same, so compare the default (the env var only overrides it)."""
    if isinstance(v, str) and v.startswith("${") and v.endswith("}") and ":" in v:
        depth, i = 0, 0
        inner = v[2:-1]
        # split on the first ':' that is not inside a nested ${...}
        for i, ch in enumerate(inner):
            if inner.startswith("${", i):
                depth += 1
            elif ch == "}" and depth:
                depth -= 1
            elif ch == ":" and depth == 0:
                return str(inner[i + 1:])
    return v if isinstance(v, str) else v


def flat(d, prefix=""):
    out = {}
    if isinstance(d, dict):
        for k, v in d.items():
            out.update(flat(v, f"{prefix}.{k}" if prefix else str(k)))
    elif isinstance(d, list):
        out[prefix] = repr(d)
    else:
        out[prefix] = str(norm(d))  # str(): a literal 6379 and the default of ${REDIS_PORT:6379} compare equal
    return out


def load(path):
    with open(path, encoding="utf-8-sig") as f:
        return flat(yaml.safe_load(f) or {})


def main():
    if not os.path.isdir(CONFIG):
        sys.exit(f"Config repo not found at {CONFIG}")
    problems = 0
    shared = load(os.path.join(CONFIG, "application.yml")) if os.path.exists(os.path.join(CONFIG, "application.yml")) else {}
    for path in sorted(glob.glob(os.path.join(CONFIG, "*.yml"))):
        name = os.path.basename(path)[:-4]
        if name == "application":
            continue
        service_yml = os.path.join(HERE, name, "src", "main", "resources", "application.yml")
        if not os.path.exists(service_yml):
            print(f"DRIFT  {name}: config file has no matching service")
            problems += 1
            continue
        cfg, svc = load(path), load(service_yml)
        for key, value in cfg.items():
            if key not in svc:
                if key not in ONLY_IN_CONFIG_OK:
                    print(f"DRIFT  {name}: '{key}' is in the config repo but the service does not use it")
                    problems += 1
            elif svc[key] != value and key not in DIFFERENT_OK.get(name, set()):
                print(f"DRIFT  {name}: '{key}' config={value[:60]} service={svc[key][:60]}")
                problems += 1
    # The shared file applies to every service: check each shared value against every service that sets it.
    for key, value in shared.items():
        for service_yml in sorted(glob.glob(os.path.join(HERE, "*", "src", "main", "resources", "application.yml"))):
            svc_name = os.path.basename(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(service_yml)))))
            if svc_name == "config-server":
                continue
            svc = load(service_yml)
            own_config = os.path.join(CONFIG, svc_name + ".yml")
            if os.path.exists(own_config) and key in load(own_config):
                continue  # the per-service config file overrides the shared value; that file is checked above
            if key in svc and svc[key] != value:
                print(f"DRIFT  application.yml (shared): '{key}' config={value[:50]} but {svc_name}={svc[key][:50]}")
                problems += 1
    if problems:
        print(f"\n{problems} difference(s) between the config repo and the services.")
        sys.exit(1)
    print("Config repo matches the services (no drift).")


if __name__ == "__main__":
    main()
