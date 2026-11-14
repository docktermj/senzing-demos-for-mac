#!/usr/bin/env python3
"""Generate the Java visualization server's static assets from the shipped reference.

The Truth Set visualization server is written in Java (the bootcamper's chosen language,
INV-090). Its browser front end -- the single-page D3 app -- is language-independent, so
rather than re-typing ~950 lines of HTML/CSS/JS this generator extracts them from the
shipped reference `scripts/senzing_viz_server.py`, together with the Senzing brand tokens
and the vendored D3, so the Java server serves exactly the reference page and palette.

Writes (under src/server/assets/):
  page_template.html   the reference PAGE, placeholders intact (__TITLE__, __D3_SCRIPT__, ...)
  probe_body_live.html the reference live Search/Probe tab body
  brand.json           brand tokens + source-color allocation constants
  vendor/d3.v7.min.js  the vendored D3 v7 (rendering stays offline, INV-091)

Usage: python3 -I src/server/generate_assets.py <plugin-root>
"""
import importlib.util
import json
import os
import shutil
import sys


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: generate_assets.py <plugin-root>")
    plugin = sys.argv[1]
    ref = os.path.join(plugin, "scripts", "senzing_viz_server.py")
    d3 = os.path.join(plugin, "scripts", "vendor", "d3.v7.min.js")
    for p in (ref, d3):
        if not os.path.isfile(p):
            sys.exit(f"missing reference asset: {p}")

    sys.path.insert(0, os.path.dirname(ref))  # so the reference finds brand_tokens.py
    spec = importlib.util.spec_from_file_location("senzing_viz_reference", ref)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
    os.makedirs(os.path.join(out, "vendor"), exist_ok=True)
    with open(os.path.join(out, "page_template.html"), "w", encoding="utf-8") as fh:
        fh.write(mod.PAGE)
    with open(os.path.join(out, "probe_body_live.html"), "w", encoding="utf-8") as fh:
        fh.write(mod.PROBE_BODY_LIVE)
    brand = {
        "brand": mod._BRAND,
        "source_colors": mod.SOURCE_COLORS,
        "fallback_colors": mod.FALLBACK_COLORS,
        "source_strokes": mod.SOURCE_STROKES,
        "source_stroke_widths": mod.SOURCE_STROKE_WIDTHS,
        "source_fill_shades": mod.SOURCE_FILL_SHADES,
        # A worked example of the reference allocator, so the Java port can be checked
        # against it (the server prints its own allocation for the same input).
        "check_example": {
            "input": ["CUSTOMERS", "REFERENCE", "WATCHLIST", "CUSTOMERS|WATCHLIST", "ZETA"],
            "expected": mod.color_for_sources(
                ["CUSTOMERS", "REFERENCE", "WATCHLIST", "CUSTOMERS|WATCHLIST", "ZETA"]),
        },
    }
    with open(os.path.join(out, "brand.json"), "w", encoding="utf-8") as fh:
        json.dump(brand, fh, indent=2)
    shutil.copyfile(d3, os.path.join(out, "vendor", "d3.v7.min.js"))
    print(f"assets written to {out}")


if __name__ == "__main__":
    main()
