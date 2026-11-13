#!/usr/bin/env python3
"""Generate the visualization server's browser assets from the bootcamp's reference server.

The Java visualization server (VizServer.java) implements the model, the JSON API and the
snapshot writer itself. The page it serves -- HTML, CSS and the D3 client code -- runs in the
browser and does not depend on the server's language, so it is taken verbatim from the shipped
reference (scripts/senzing_viz_server.py) rather than re-typed: the reference page already
implements the contract's tab ids, deep-linking, capture mode, settled signal, legends and
escaping, and a hand-copied page would drift from it.

Writes into src/server/assets/:
  page.html        the reference PAGE template, placeholders intact (__TITLE__, __DATA_SHIM__, ...)
  probe_live.html  the live Search / Probe tab body
  brand.json       Senzing brand tokens: CSS root variables, fonts, accents, source palette
  d3.v7.min.js     the vendored D3 (offline rendering, INV-091)

Usage: python3 src/server/generate_assets.py <path-to-plugin>/scripts/senzing_viz_server.py
"""
import importlib.util
import json
import os
import shutil
import sys


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    ref = os.path.abspath(sys.argv[1])
    d3 = os.path.join(os.path.dirname(ref), "vendor", "d3.v7.min.js")
    if not os.path.isfile(d3):
        sys.exit("ERROR: vendored D3 asset d3.v7.min.js not found at %s; refusing to generate "
                 "a page without it (INV-091)." % d3)
    spec = importlib.util.spec_from_file_location("senzing_viz_reference", ref)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "page.html"), "w", encoding="utf-8") as fh:
        fh.write(mod.PAGE)
    with open(os.path.join(out, "probe_live.html"), "w", encoding="utf-8") as fh:
        fh.write(mod.PROBE_BODY_LIVE)
    b = mod._BRAND
    brand = {
        "root_vars": ("--navy:%(dark)s;--blue:%(accent)s;--gold:%(accent_hot)s;--ink:%(ink)s;"
                      "--muted:%(muted)s;--line:%(line)s;--bg:%(bg)s;--accent-soft:%(accent_soft)s;"
                      "--green:%(green)s" % b),
        "font": b["font"],
        "code_font": b["code_font"],
        "accent": b["accent"],
        "accent_hot": b["accent_hot"],
        "source_colors": mod.SOURCE_COLORS,
        "fallback_colors": mod.FALLBACK_COLORS,
        "source_strokes": mod.SOURCE_STROKES,
        "source_stroke_widths": mod.SOURCE_STROKE_WIDTHS,
        "source_fill_shades": mod.SOURCE_FILL_SHADES,
        # Cross-check for the Java port of color_for_sources (VizServer.colorForSources).
        "color_check": {"codes": ["CUSTOMERS", "CUSTOMERS|REFERENCE", "REFERENCE", "WATCHLIST",
                                  "X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8"],
                        "expected": mod.color_for_sources(
                            ["CUSTOMERS", "CUSTOMERS|REFERENCE", "REFERENCE", "WATCHLIST",
                             "X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8"])},
    }
    with open(os.path.join(out, "brand.json"), "w", encoding="utf-8") as fh:
        json.dump(brand, fh, indent=2)
    shutil.copyfile(d3, os.path.join(out, "d3.v7.min.js"))
    print("assets written to", out)


if __name__ == "__main__":
    main()
