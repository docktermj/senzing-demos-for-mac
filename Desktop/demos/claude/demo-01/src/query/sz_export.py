#! /usr/bin/env python3
"""Shared helper: open the engine once and stream the entity export.

Factory-lifetime rule: the factory object is kept alive (returned with the engine) for as long as the engine is
used -- dropping it destroys the engine ("engine object has been destroyed").
Calls: SzAbstractFactoryCore(...).create_engine(); export_json_entity_report(flags) / fetch_next / close_export_report
(reporting_guide topic='export'; get_sdk_reference flags and response_schemas).
"""
import json
import os
from pathlib import Path

from senzing import SzEngineFlags
from senzing_core import SzAbstractFactoryCore

SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
EXPORT_FLAGS = SzEngineFlags.SZ_EXPORT_DEFAULT_FLAGS | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_SUMMARY


def open_engine(name):
    factory = SzAbstractFactoryCore(Path(name).stem, SETTINGS, verbose_logging=False)
    return factory, factory.create_engine()  # caller keeps BOTH references


def export_rows(engine, flags=EXPORT_FLAGS):
    handle = engine.export_json_entity_report(flags)
    try:
        while row := engine.fetch_next(handle):
            yield json.loads(row)
    finally:
        engine.close_export_report(handle)
