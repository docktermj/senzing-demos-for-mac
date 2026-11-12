#! /usr/bin/env python3
"""Register this project's DATA_SOURCE codes in the Senzing configuration (run before loading).

From senzing/code-snippets-v4 python/configuration/register_data_sources.py (sdk_guide topic='configure',
data_sources=[...]). Deviations, both for re-runnability: the snippet's sample codes are replaced with
this project's codes, and codes already in the data source registry are skipped, with no new config
registered when nothing changed. SDK calls are unchanged from the snippet.
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_SOURCES = ("CRM_SYSTEM", "BILLING_SYSTEM", "SUPPORT_TICKETS")

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_configmanager = sz_factory.create_configmanager()

    current_config_id = sz_configmanager.get_default_config_id()
    if not current_config_id:
        print("No default configuration is registered; seed one first (init_default_config).", file=sys.stderr)
        sys.exit(1)
    sz_config = sz_configmanager.create_config_from_config_id(current_config_id)

    existing = {d["DSRC_CODE"] for d in json.loads(sz_config.get_data_source_registry()).get("DATA_SOURCES", [])}
    to_add = [code for code in DATA_SOURCES if code not in existing]
    for data_source in to_add:
        sz_config.register_data_source(data_source)

    if to_add:
        new_config = sz_config.export()
        new_config_id = sz_configmanager.register_config(new_config, f"Register {', '.join(to_add)}")
        sz_configmanager.replace_default_config_id(current_config_id, new_config_id)
        print(f"Registered {to_add}; new default config ID: {new_config_id}")
    else:
        print(f"Already registered: {list(DATA_SOURCES)}; default config ID unchanged: {current_config_id}")
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
