#! /usr/bin/env python3
"""Truth Set visualization, Step 1.2: register the Truth Set's DATA_SOURCE codes.

Based on the official snippet src/resources/scaffold/configuration/register_data_sources.py.
Deviations from the snippet (both for re-runnability):
  - the codes are read from the data file, so only codes present in the data are registered;
  - codes already in the registry are skipped, and no new config is registered when nothing changed.
Calls (get_sdk_reference, methods):
  SzConfig.get_data_source_registry() -> JSON string   (live shape: DATA_SOURCES[].DSRC_CODE)
  SzConfig.register_data_source(data_source_code) -> JSON string
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_FILE = Path(sys.argv[1] if len(sys.argv) > 1 else "src/system_verification/truthset_data.jsonl")


def codes_in_data(path):
    codes = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            codes.add(json.loads(line)["DATA_SOURCE"])
    return sorted(codes)


try:
    wanted = codes_in_data(DATA_FILE)
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_configmanager = sz_factory.create_configmanager()

    current_config_id = sz_configmanager.get_default_config_id()
    sz_config = sz_configmanager.create_config_from_config_id(current_config_id)

    registry = json.loads(sz_config.get_data_source_registry())
    existing = {d["DSRC_CODE"] for d in registry.get("DATA_SOURCES", [])}
    to_add = [code for code in wanted if code not in existing]

    for data_source in to_add:
        sz_config.register_data_source(data_source)

    if to_add:
        new_config = sz_config.export()
        new_config_id = sz_configmanager.register_config(new_config, f"Register {', '.join(to_add)}")
        sz_configmanager.replace_default_config_id(current_config_id, new_config_id)
        print(f"Registered {to_add}; new default config ID: {new_config_id}")
    else:
        print(f"Already registered: {wanted}; default config ID unchanged: {current_config_id}")
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
