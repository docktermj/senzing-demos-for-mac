import java.util.Objects;

import javax.json.JsonObject;

final class TruthSetPage {
    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="color-scheme" content="light">
              <title>@@TITLE@@</title>
              <style>
                :root {
                  --obsidian: #0F0D0C;
                  --deep: #18160F;
                  --surface-dark: #201E16;
                  --ember-hot: #FF4E1F;
                  --ember: #F57826;
                  --ember-soft: #FDEEE3;
                  --white: #FFFFFF;
                  --warm: #FAF8F3;
                  --ink: #18160F;
                  --body: #4A4640;
                  --line: #E5DFD3;
                  --green: #1D9E75;
                  --signal-caution: #C8922A;
                  --signal-negative: #B43A32;
                  --font: Roboto, -apple-system, "Segoe UI", Helvetica, Arial, sans-serif;
                  --code: "Fira Code", "Courier New", Courier, monospace;
                }
                * { box-sizing: border-box; }
                body { margin: 0; background: var(--warm); color: var(--body); font: 15px/1.5 var(--font); }
                button, input { font: inherit; }
                button { cursor: pointer; }
                .hero { padding: 22px clamp(16px, 4vw, 56px) 20px; background: var(--deep); color: var(--white); }
                .eyebrow { color: var(--ember-hot); font-size: 11px; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; }
                h1 { margin: 5px 0 2px; color: var(--white); font-size: clamp(24px, 3vw, 36px); letter-spacing: -.03em; }
                .dataset { margin: 0; color: rgba(255,255,255,.72); }
                .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(135px, 1fr)); gap: 1px; margin: 0; padding: 0 clamp(16px, 4vw, 56px); background: var(--line); }
                .metric { padding: 12px 14px; background: var(--white); }
                .metric dt { color: var(--body); font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; }
                .metric dd { margin: 2px 0 0; color: var(--ink); font-size: 23px; font-weight: 750; }
                .tabbar { display: flex; gap: 4px; overflow-x: auto; padding: 12px clamp(12px, 4vw, 56px) 0; background: var(--deep); }
                .tab { flex: 0 0 auto; border: 0; border-radius: 7px 7px 0 0; padding: 10px 14px; background: transparent; color: rgba(255,255,255,.7); font-weight: 700; }
                .tab:hover, .tab:focus-visible { color: var(--white); outline: 2px solid var(--ember-hot); outline-offset: -2px; }
                .tab[aria-selected="true"] { background: var(--warm); color: var(--ink); }
                main { padding: 20px clamp(12px, 3vw, 40px) 52px; }
                .panel { display: none; }
                .panel.active { display: block; }
                .panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin: 0 0 14px; }
                h2 { margin: 0; color: var(--ink); font-size: 22px; letter-spacing: -.02em; }
                h3 { margin: 0 0 10px; color: var(--ink); font-size: 16px; }
                p { margin: 6px 0 12px; }
                .muted { color: #777168; }
                .note { border-left: 4px solid var(--ember); margin: 10px 0; padding: 9px 12px; background: var(--white); }
                .notice { border-left-color: #3B6EA5; }
                .warning { border-left-color: #C8922A; }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; }
                .card { min-width: 0; border: 1px solid var(--line); border-radius: 10px; padding: 16px; background: var(--white); box-shadow: 0 4px 14px rgba(24,22,15,.04); }
                .toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
                .control { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--line); border-radius: 7px; padding: 7px 10px; background: var(--white); color: var(--ink); }
                .control input { accent-color: var(--ember); }
                .primary { border: 0; border-radius: 7px; padding: 8px 12px; background: var(--ember); color: var(--white); font-weight: 750; }
                .primary:hover { background: #d96117; }
                .secondary { border: 1px solid var(--line); border-radius: 7px; padding: 7px 10px; background: var(--white); color: var(--ink); font-weight: 650; }
                .secondary:hover { border-color: var(--ember); }
                #graphSvg { display: block; width: 100%; height: min(68vh, 650px); min-height: 430px; border: 1px solid var(--line); border-radius: 9px; background: #fffdfa; }
                .legend { display: flex; flex-wrap: wrap; gap: 7px; margin: 10px 0 0; }
                .legend button { display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--line); border-radius: 999px; padding: 5px 9px; background: var(--white); color: var(--body); font-size: 12px; }
                .legend button[aria-pressed="false"] { opacity: .42; }
                .swatch { display: inline-block; width: 12px; height: 12px; border: 1px solid #aaa; border-radius: 50%; }
                .line-swatch { display: inline-block; width: 18px; border-top: 3px solid currentColor; }
                .line-swatch.dashed { border-top-style: dashed; }
                .line-swatch.dotted { border-top-style: dotted; }
                .chart { min-height: 160px; }
                .bar-row { display: grid; grid-template-columns: minmax(105px, 190px) 1fr 54px; align-items: center; gap: 9px; margin: 8px 0; }
                .bar-label { overflow: hidden; color: var(--ink); font: 12px/1.25 var(--code); text-overflow: ellipsis; white-space: nowrap; }
                .bar-button { display: block; width: 100%; min-width: 2px; height: 20px; border: 0; border-radius: 3px; background: var(--ember); }
                .bar-button:hover, .bar-button:focus-visible { outline: 2px solid var(--deep); outline-offset: 2px; }
                .bar-value { color: var(--ink); text-align: right; font-variant-numeric: tabular-nums; }
                .entity-list { display: grid; gap: 8px; }
                .entity-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; border: 1px solid var(--line); border-radius: 8px; padding: 10px; background: var(--white); }
                .entity-name { color: var(--ink); font-weight: 750; overflow-wrap: anywhere; }
                .entity-meta { color: #777168; font-size: 12px; }
                .legend-heading { flex: 0 0 100%; margin-top: 5px; color: var(--ink); font-size: 12px; font-weight: 750; }
                .actions { display: flex; flex-wrap: wrap; gap: 5px; }
                .actions button { border: 1px solid var(--line); border-radius: 6px; padding: 4px 7px; background: var(--warm); color: var(--ink); font-size: 12px; font-weight: 650; }
                .actions button:hover { border-color: var(--ember); }
                .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
                .table-wrap { overflow: auto; }
                table { width: 100%; border-collapse: collapse; }
                th, td { padding: 9px 10px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: middle; }
                th { color: var(--ink); font-size: 12px; }
                td { color: var(--body); }
                .clickable { width: 100%; border: 0; padding: 6px; background: transparent; color: var(--ink); text-align: left; }
                .clickable:hover { color: #b94c0d; text-decoration: underline; }
                .heat { width: 100%; min-width: 70px; border: 1px solid rgba(24,22,15,.12); border-radius: 6px; padding: 10px 6px; color: var(--ink); font-weight: 750; }
                .score-bars { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
                .score-card { border: 1px solid var(--line); border-radius: 8px; padding: 12px; }
                .search-form { display: flex; flex-wrap: wrap; gap: 8px; }
                .search-form input { flex: 1 1 220px; border: 1px solid var(--line); border-radius: 7px; padding: 9px 11px; }
                .chips { display: flex; flex-wrap: wrap; gap: 7px; margin: 12px 0; }
                .chip { border: 1px solid #f5c29e; border-radius: 999px; padding: 6px 10px; background: var(--ember-soft); color: #87400f; }
                dialog { width: min(860px, calc(100vw - 24px)); max-height: 88vh; border: 1px solid var(--line); border-radius: 12px; padding: 0; color: var(--body); box-shadow: 0 18px 70px rgba(0,0,0,.32); }
                dialog::backdrop { background: rgba(15,13,12,.55); }
                .modal-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; position: sticky; top: 0; padding: 14px 18px; background: var(--deep); color: var(--white); }
                .modal-head h2 { color: var(--white); font-size: 18px; }
                .modal-head button { border: 1px solid rgba(255,255,255,.25); border-radius: 6px; padding: 5px 9px; background: transparent; color: var(--white); }
                .modal-body { padding: 16px 18px; overflow: auto; }
                pre { overflow: auto; max-height: 60vh; border-radius: 8px; padding: 12px; background: #f2efe8; color: var(--ink); font: 12px/1.45 var(--code); white-space: pre-wrap; overflow-wrap: anywhere; }
                .explanation-step { margin: 12px 0; border: 1px solid var(--line); border-radius: 8px; padding: 12px; background: var(--white); }
                .score-list { display: grid; gap: 7px; }
                .score-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--line); padding: 7px 0; }
                .score-label { min-width: 0; overflow-wrap: anywhere; }
                .status-badge { flex: 0 0 auto; border-radius: 999px; padding: 3px 8px; font-size: 11px; font-weight: 750; }
                .status-positive { background: #E4F3ED; color: var(--green); }
                .status-caution { background: #FFF3D6; color: var(--signal-caution); }
                .status-negative { background: #FCE8E6; color: var(--signal-negative); }
                .raw-details { margin-top: 12px; }
                .raw-details summary { color: var(--body); cursor: pointer; font-weight: 700; }
                dialog[open] { animation: modal-enter .16s ease-out; }
                @keyframes modal-enter { from { opacity: 0; transform: translateY(8px) scale(.99); } to { opacity: 1; transform: translateY(0) scale(1); } }
                @media (prefers-reduced-motion: reduce) { dialog[open] { animation: none; } }
                .empty { border: 1px dashed #c9c1b4; border-radius: 8px; padding: 16px; color: #777168; text-align: center; }
                footer { margin-top: 24px; color: #777168; font-size: 12px; text-align: center; }
                :root[data-capture-mode="true"] .hero { padding: 6px 30px 4px; }
                :root[data-capture-mode="true"] .eyebrow { font-size: 9px; }
                :root[data-capture-mode="true"] h1 { font-size: clamp(20px, 2.2vw, 26px); }
                :root[data-capture-mode="true"] .dataset { font-size: 12px; }
                :root[data-capture-mode="true"] .summary { padding: 0 30px; }
                :root[data-capture-mode="true"] .metric { padding: 4px 8px; }
                :root[data-capture-mode="true"] .metric dt { font-size: 9px; }
                :root[data-capture-mode="true"] .metric dd { font-size: 15px; }
                :root[data-capture-mode="true"] .tabbar { padding: 4px 24px 0; }
                :root[data-capture-mode="true"] .tab { padding: 5px 9px; }
                :root[data-capture-mode="true"] main { padding: 6px 24px 12px; }
                :root[data-capture-mode="true"] .panel-head { margin-bottom: 4px; }
                :root[data-capture-mode="true"] .panel-head p { margin: 2px 0 6px; }
                :root[data-capture-mode="true"] h2 { font-size: 17px; }
                :root[data-capture-mode="true"] .control { padding: 4px 8px; }
                :root[data-capture-mode="true"] #graphNote { margin: 5px 0; padding: 5px 8px; line-height: 1.25; }
                :root[data-capture-mode="true"] #graphSvg { height: 240px; min-height: 240px; }
                :root[data-capture-mode="true"] .legend { gap: 5px; margin-top: 5px; }
                :root[data-capture-mode="true"] .legend-heading { margin-top: 3px; font-size: 11px; }
                :root[data-capture-mode="true"] .legend button { padding: 3px 7px; font-size: 11px; }
                @media (max-width: 680px) {
                  .summary { grid-template-columns: repeat(2, 1fr); }
                  .bar-row { grid-template-columns: 90px 1fr 40px; }
                  .entity-row { align-items: flex-start; flex-direction: column; }
                  #graphSvg { min-height: 360px; }
                }
              </style>
            </head>
            <body>
              <header class="hero">
                <div class="eyebrow">Entity resolution visualization</div>
                <h1>@@TITLE@@</h1>
                <p class="dataset">Dataset: @@DATASET@@</p>
              </header>
              <dl class="summary" id="summary" aria-label="Dataset summary"></dl>
              <nav class="tabbar" role="tablist" aria-label="Visualization tabs">
                <button class="tab" id="navbtn-graph" role="tab" aria-controls="tab-graph" aria-selected="true" data-tab="graph">Entity Graph</button>
                <button class="tab" id="navbtn-stats" role="tab" aria-controls="tab-stats" aria-selected="false" data-tab="stats">Merge Statistics</button>
                <button class="tab" id="navbtn-matchkeys" role="tab" aria-controls="tab-matchkeys" aria-selected="false" data-tab="matchkeys">Match Keys</button>
                <button class="tab" id="navbtn-features" role="tab" aria-controls="tab-features" aria-selected="false" data-tab="features">Feature Scores</button>
                <button class="tab" id="navbtn-overlap" role="tab" aria-controls="tab-overlap" aria-selected="false" data-tab="overlap">Cross-Source</button>
                <button class="tab" id="navbtn-probe" role="tab" aria-controls="tab-probe" aria-selected="false" data-tab="probe">Search / Probe</button>
              </nav>
              <main>
                <section class="panel active" id="tab-graph" role="tabpanel" aria-labelledby="navbtn-graph">
                  <div class="panel-head"><div><h2>Entity Graph</h2><p class="muted">Each node is a resolved entity; data-source combinations identify cross-source links.</p></div></div>
                  <div class="toolbar" id="graphControls">
                    <label class="control" id="relationshipControl" hidden><input type="checkbox" id="relationshipMode"> Show only entities with relationships</label>
                    <label class="control"><input type="checkbox" id="nodeLabels"> Node labels</label>
                    <label class="control"><input type="checkbox" id="edgeLabels"> Edge labels</label>
                    <button class="secondary" id="resetGraph">Reset view</button>
                  </div>
                  <p class="note" id="graphNote" aria-live="polite"></p>
                  <svg id="graphSvg" role="img" aria-label="Force-directed graph of resolved entities"></svg>
                  <div id="sourceLegendWrap"><h3>Data-source legend</h3><div class="legend" id="sourceLegend" aria-label="Filter by data source"></div></div>
                  <div id="relationshipLegendWrap" hidden><h3>Relationship legend</h3><div class="legend" id="relationshipLegend" aria-label="Filter by relationship type"></div></div>
                </section>
                <section class="panel" id="tab-stats" role="tabpanel" aria-labelledby="navbtn-stats">
                  <div class="panel-head"><div><h2>Merge Statistics</h2><p class="muted">Select a bar to inspect the entities in that records-per-entity bucket.</p></div></div>
                  <div class="grid">
                    <div class="card"><h3>Records per entity</h3><div id="histogram" class="chart"></div></div>
                    <div class="card"><h3>Largest resolved entities</h3><div id="largestEntities" class="entity-list"></div></div>
                  </div>
                </section>
                <section class="panel" id="tab-matchkeys" role="tabpanel" aria-labelledby="navbtn-matchkeys">
                  <div class="panel-head"><div><h2>Match Keys</h2><p class="muted">Frequency of the per-record match keys that contributed to multi-record entities.</p></div></div>
                  <p class="note" id="matchKeyNote" hidden></p><div id="matchKeysChart" class="card chart"></div>
                </section>
                <section class="panel" id="tab-features" role="tabpanel" aria-labelledby="navbtn-features">
                  <div class="panel-head"><div><h2>Feature Scores</h2><p class="muted">Feature agreement in a bounded sample of multi-record entities.</p></div></div>
                  <p class="note" id="featureNote"></p><div id="featureCharts" class="score-bars"></div>
                </section>
                <section class="panel" id="tab-overlap" role="tabpanel" aria-labelledby="navbtn-overlap">
                  <div class="panel-head"><div><h2>Cross-Source</h2><p class="muted">How many resolved entities contain records from each pair of data sources.</p></div></div>
                  <p class="note" id="overlapNote" hidden></p><div class="card table-wrap" id="overlapTable"></div>
                </section>
                <section class="panel" id="tab-probe" role="tabpanel" aria-labelledby="navbtn-probe">
                  <div class="panel-head"><div><h2>Search / Probe</h2><p class="muted">Search a name, then inspect the entity evidence and resolution explanation.</p></div></div>
                  <div class="card">
                    <form id="searchForm" class="search-form"><label class="sr-only" for="searchInput">Name to search</label><input id="searchInput" type="search" autocomplete="off" placeholder="Enter a person or organization name"><button class="primary" type="submit">Search</button></form>
                    <div class="chips" id="searchChips"></div>
                    <p class="muted" id="searchHint">Search attempts NAME_FULL, then NAME_ORG if no result is returned. An empty result does not prove the record is absent.</p>
                    <button class="secondary" id="showMerged">Show all merged entities</button>
                  </div>
                  <div id="searchStatus" aria-live="polite"></div><div id="searchResults" class="entity-list" style="margin-top:12px"></div>
                </section>
                <footer>Offline snapshot: entity records and aggregate views are embedded; Why?, How?, and live search require the running server.</footer>
              </main>
              <dialog id="detailDialog" aria-labelledby="dialogTitle">
                <div class="modal-head"><h2 id="dialogTitle">Entity details</h2><button id="dialogClose" aria-label="Close dialog">Close</button></div>
                <div class="modal-body" id="dialogBody"></div>
              </dialog>
              <script>@@D3@@</script>
              <script>
                window.__SNAPSHOT__ = @@SNAPSHOT@@;
                window.__DATA__ = @@DATA@@;
                window.__NONCE__ = "@@NONCE@@";
              </script>
              <script>
                (() => {
                  const TYPES = {
                    possibly_same: {label: "Possibly the same entity", color: "#F57826", dash: ""},
                    possibly_related: {label: "Possibly related", color: "#3B6EA5", dash: "7 4"},
                    disclosed: {label: "Disclosed relationship", color: "#C8922A", dash: "2 4"},
                    ambiguous: {label: "Ambiguous", color: "#8b5cf6", dash: "9 3 2 3"}
                  };
                  const tabs = ["graph", "stats", "matchkeys", "features", "overlap", "probe"];
                  const scoreBucketKinds = new Map([
                    ["SAME", "positive"], ["CLOSE", "positive"],
                    ["PLUS", "caution"], ["LIKELY", "caution"], ["PLAUSIBLE", "caution"],
                    ["UNLIKELY", "negative"], ["NO_CHANCE", "negative"]
                  ]);
                  const captureMode = new URLSearchParams(window.location.search).get("capture") === "1";
                  if (captureMode) document.documentElement.dataset.captureMode = "true";
                  const captureLabelLimit = 40;
                  const interactiveLabelLimit = 150;
                  let data = null;
                  let activeTab = null;
                  let graphData = null;
                  let graphSimulation = null;
                  let graphZoom = null;
                  let relationshipMode = false;
                  let graphModeChosen = false;
                  let nodeLabelsOn = false;
                  let edgeLabelsOn = false;
                  let graphLabelsChosen = false;
                  let selectedSources = new Set();
                  let selectedSourceSets = new Set();
                  let selectedRelationships = new Set();
                  let allSourceFilters = true;
                  let allRelationshipFilters = true;

                  const byId = id => document.getElementById(id);
                  const number = value => new Intl.NumberFormat().format(Number(value || 0));
                  const safeText = value => value == null ? "" : String(value);
                  const json = value => JSON.stringify(value, null, 2);
                  const make = (tag, className, text) => {
                    const node = document.createElement(tag);
                    if (className) node.className = className;
                    if (text != null) node.textContent = text;
                    return node;
                  };
                  const fitLabel = (value, max) => {
                    const text = safeText(value);
                    if (text.length <= max) return text;
                    const keep = Math.max(2, max - 1);
                    const left = Math.ceil(keep / 2);
                    const right = Math.floor(keep / 2);
                    return text.slice(0, left) + "…" + text.slice(text.length - right);
                  };
                  const uniqueLabels = (values, max) => {
                    const labels = values.map(value => fitLabel(value, max));
                    for (let pass = 0; pass <= values.length; pass++) {
                      const groups = new Map();
                      labels.forEach((label, index) => {
                        if (!groups.has(label)) groups.set(label, []);
                        groups.get(label).push(index);
                      });
                      const collisions = [...groups.values()].filter(indexes =>
                        new Set(indexes.map(index => safeText(values[index]))).size > 1);
                      if (!collisions.length) break;
                      collisions.forEach(indexes => indexes.forEach(index => {
                        const suffix = " #" + (index + 1);
                        labels[index] = fitLabel(values[index], Math.max(4, max - suffix.length)) + suffix;
                      }));
                    }
                    return labels;
                  };
                  const api = async path => {
                    const response = await fetch(path, {headers: {"Accept": "application/json"}});
                    const body = await response.json();
                    if (!response.ok) throw new Error(body.error || ("HTTP " + response.status));
                    return body;
                  };
                  const actionButtons = entityId => {
                    const actions = make("div", "actions");
                    [["Records", "records"], ["Why?", "why"], ["How?", "how"]].forEach(([label, action]) => {
                      const button = make("button", "", label);
                      button.type = "button";
                      button.addEventListener("click", () => openAction(entityId, action));
                      actions.append(button);
                    });
                    return actions;
                  };
                  const entityRow = entity => {
                    const row = make("div", "entity-row");
                    const info = make("div");
                    info.append(make("div", "entity-name", entity.entity_name || ("Entity " + entity.entity_id)));
                    info.append(make("div", "entity-meta", "Entity " + entity.entity_id + " · " + number(entity.record_count) + " records"));
                    const matchKeys = [...new Set((entity.match_keys || []).filter(Boolean))];
                    if (matchKeys.length) {
                      const keys = make("div", "entity-meta", "Match keys: " + matchKeys.join(", "));
                      keys.title = matchKeys.join(", ");
                      info.append(keys);
                    }
                    row.append(info, actionButtons(entity.entity_id));
                    return row;
                  };
                  const entityList = (items, container) => {
                    container.replaceChildren();
                    if (!items || !items.length) {
                      container.append(make("div", "empty", "No entities to show."));
                      return;
                    }
                    items.forEach(item => container.append(entityRow(item)));
                  };
                  const openModal = (title, content) => {
                    byId("dialogTitle").textContent = title;
                    byId("dialogBody").replaceChildren(content);
                    const dialog = byId("detailDialog");
                    if (!dialog.open) dialog.showModal();
                  };
                  const textPanel = (title, value) => {
                    const wrapper = make("div");
                    wrapper.append(make("h3", "", title));
                    const pre = make("pre");
                    pre.textContent = typeof value === "string" ? value : json(value);
                    wrapper.append(pre);
                    return wrapper;
                  };
                  const rawDetails = value => {
                    const details = make("details", "raw-details");
                    details.append(make("summary", "", "Raw SDK response"));
                    const pre = make("pre");
                    pre.textContent = json(value);
                    details.append(pre);
                    return details;
                  };
                  const scoreBadge = score => {
                    const bucket = safeText(score.SCORE_BUCKET) || "Not scored";
                    const normalized = bucket.toUpperCase();
                    const kind = scoreBucketKinds.get(normalized) || "caution";
                    const numeric = score.SCORE == null ? "" : " · " + number(score.SCORE);
                    return make("span", "status-badge status-" + kind, bucket + numeric);
                  };
                  const appendFeatureScores = (container, matchInfo) => {
                    const families = matchInfo.FEATURE_SCORES || {};
                    const rows = [];
                    Object.entries(families).forEach(([family, values]) => {
                      if (Array.isArray(values)) values.forEach(score => rows.push([family, score]));
                    });
                    container.append(make("h3", "", "Feature evidence"));
                    if (!rows.length) {
                      container.append(make("p", "muted", "No feature-score comparisons were returned."));
                      return;
                    }
                    const list = make("div", "score-list");
                    rows.forEach(([family, score]) => {
                      const row = make("div", "score-row");
                      const left = safeText(score.INBOUND_FEAT_DESC);
                      const right = safeText(score.CANDIDATE_FEAT_DESC);
                      row.append(make("div", "score-label", family + ": " + [left, right].filter(Boolean).join(" ↔ ")));
                      row.append(scoreBadge(score));
                      list.append(row);
                    });
                    container.append(list);
                  };
                  const whyPanel = response => {
                    const wrapper = make("div");
                    const results = (response.result && response.result.WHY_RESULTS) || [];
                    if (!results.length) {
                      wrapper.append(make("p", "note warning", "The engine returned no comparison explanation for this entity."));
                    }
                    results.forEach((item, index) => {
                      const card = make("section", "explanation-step");
                      const info = item.MATCH_INFO || {};
                      card.append(make("h3", "", results.length > 1 ? "Comparison " + (index + 1) : "Why these records were compared"));
                      const groups = [
                        (item.FOCUS_RECORDS || []).map(record => [record.DATA_SOURCE, record.RECORD_ID].filter(Boolean).join(" / ")).filter(Boolean),
                        (item.FOCUS_RECORDS_2 || []).map(record => [record.DATA_SOURCE, record.RECORD_ID].filter(Boolean).join(" / ")).filter(Boolean)
                      ].filter(group => group.length);
                      if (groups.length) {
                        card.append(make("p", "muted", "Records compared: " + groups.map(group => group.join(", ")).join(" ↔ ")));
                      }
                      const findings = [
                        ["Match classification", info.MATCH_LEVEL_CODE],
                        ["Resolution rule", info.WHY_ERRULE_CODE],
                        ["Why key", info.WHY_KEY]
                      ].filter(([, value]) => value);
                      findings.forEach(([label, value]) => card.append(make("p", "entity-meta", label + ": " + value)));
                      appendFeatureScores(card, info);
                      wrapper.append(card);
                    });
                    wrapper.append(rawDetails(response.result || response));
                    return wrapper;
                  };
                  const howRecords = virtualEntity => (virtualEntity.MEMBER_RECORDS || [])
                    .flatMap(member => member.RECORDS || [])
                    .map(record => [record.DATA_SOURCE, record.RECORD_ID].filter(Boolean).join(" / "))
                    .filter(Boolean);
                  const howPanel = response => {
                    const wrapper = make("div");
                    const result = response.result || {};
                    const how = result.HOW_RESULTS || {};
                    const steps = how.RESOLUTION_STEPS || [];
                    if (!steps.length) {
                      wrapper.append(make("p", "note", "No step-by-step resolution history was returned."));
                    }
                    steps.forEach((step, index) => {
                      const card = make("section", "explanation-step");
                      const info = step.MATCH_INFO || {};
                      card.append(make("h3", "", "Step " + number(step.STEP || index + 1)));
                      const groups = [step.VIRTUAL_ENTITY_1, step.VIRTUAL_ENTITY_2]
                        .filter(Boolean).map(howRecords).filter(group => group.length);
                      if (groups.length) {
                        const groupNames = groups.map(group => group.join(", "));
                        card.append(make("p", "muted", groups.length === 2
                          ? "This step brings together " + groupNames[0] + " and " + groupNames[1] + "."
                          : "This step carries forward " + groupNames[0] + "."));
                      }
                      if (info.MATCH_KEY) card.append(make("p", "", "The returned match key was " + info.MATCH_KEY + "."));
                      else card.append(make("p", "muted", "No match key was included for this step."));
                      if (info.ERRULE_CODE) card.append(make("p", "entity-meta", "Resolution rule: " + info.ERRULE_CODE));
                      appendFeatureScores(card, info);
                      wrapper.append(card);
                    });
                    const finalState = how.FINAL_STATE;
                    if (finalState) {
                      const virtualEntities = finalState.VIRTUAL_ENTITIES || [];
                      const reevaluation = Number(finalState.NEED_REEVALUATION || 0);
                      const unsettled = virtualEntities.length > 1 || reevaluation > 0;
                      wrapper.append(make("p", "note" + (unsettled ? " warning" : ""),
                        "Final response state: " + number(virtualEntities.length) + " virtual entity group(s); need_reevaluation = " + number(reevaluation) + "."
                        + (unsettled ? " The response shows multiple groups or pending reevaluation; treat this result as unsettled." : "")));
                      virtualEntities.forEach((entity, index) => {
                        const records = howRecords(entity);
                        if (records.length) wrapper.append(make("p", "entity-meta", "Final group " + (index + 1) + ": " + records.join(", ")));
                      });
                    }
                    wrapper.append(rawDetails(result));
                    return wrapper;
                  };
                  const offlineNote = () => make("p", "note notice", "This action needs the live app and engine. Return to the running visualization to use it.");
                  const openAction = async (entityId, action) => {
                    if (action === "records") {
                      const records = window.__SNAPSHOT__
                        ? (data.records[String(entityId)] || {entity_id: entityId, records: []})
                        : await api("/api/records?entity_id=" + encodeURIComponent(entityId));
                      if (records.error) {
                        openModal("Records", textPanel("Records unavailable", records.error));
                        return;
                      }
                      const body = make("div");
                      body.append(make("p", "muted", safeText(records.entity_name) + " · " + number(records.records.length) + " records"));
                      const table = make("div", "table-wrap");
                      const tableNode = make("table");
                      const head = make("tr");
                      ["Data source", "Record ID", "Match key"].forEach(value => head.append(make("th", "", value)));
                      tableNode.append(head);
                      records.records.forEach(record => {
                        const tr = make("tr");
                        [record.data_source, record.record_id, record.match_key || "Seed record"].forEach(value => tr.append(make("td", "", safeText(value))));
                        tableNode.append(tr);
                      });
                      table.append(tableNode);
                      body.append(table);
                      openModal("Records for " + (records.entity_name || ("Entity " + entityId)), body);
                      return;
                    }
                    if (window.__SNAPSHOT__) {
                      openModal(action === "why" ? "Why?" : "How?", offlineNote());
                      return;
                    }
                    openModal(action === "why" ? "Why?" : "How?", make("p", "muted", "Loading explanation…"));
                    try {
                      const response = await api("/api/" + action + "?entity_id=" + encodeURIComponent(entityId));
                      const body = make("div");
                      if (response.error) body.append(make("p", "note warning", response.error));
                      if (response.result) body.append(action === "why" ? whyPanel(response) : howPanel(response));
                      openModal(action === "why" ? "Why? — Entity " + entityId : "How? — Entity " + entityId, body);
                    } catch (error) {
                      openModal(action === "why" ? "Why?" : "How?", textPanel("Request failed", error.message));
                    }
                  };
                  const buildSummary = stats => {
                    const values = [
                      ["Records", stats.records_total], ["Entities", stats.entities_total],
                      ["Multi-record", stats.multi_record_entities], ["Cross-source", stats.cross_source_entities],
                      ["Relationships", stats.relationships_total], ["Data sources", stats.data_sources_total]
                    ];
                    byId("summary").replaceChildren();
                    values.forEach(([label, value]) => {
                      const item = make("div", "metric");
                      item.append(make("dt", "", label), make("dd", "", number(value)));
                      byId("summary").append(item);
                    });
                  };
                  const buildNav = stats => {
                    byId("navbtn-matchkeys").hidden = Number(stats.multi_record_entities) === 0;
                    byId("navbtn-features").hidden = Number(stats.multi_record_entities) === 0;
                    byId("navbtn-overlap").hidden = Number(stats.data_sources_total) < 2;
                    tabs.forEach(tab => byId("navbtn-" + tab).addEventListener("click", () => activate(tab)));
                  };
                  const activate = id => {
                    if (!tabs.includes(id) || byId("navbtn-" + id).hidden || id === activeTab) return;
                    activeTab = id;
                    tabs.forEach(tab => {
                      const selected = tab === id;
                      byId("navbtn-" + tab).setAttribute("aria-selected", selected ? "true" : "false");
                      byId("tab-" + tab).classList.toggle("active", selected);
                    });
                    if (id === "graph" && !graphSimulation) drawGraph();
                    if (id === "stats") drawStats();
                    if (id === "matchkeys") drawMatchKeys();
                    if (id === "features") drawFeatures();
                    if (id === "overlap") drawOverlap();
                    if (id === "probe") byId("searchInput").focus({preventScroll: true});
                    const url = new URL(window.location.href);
                    url.searchParams.set("tab", id);
                    history.replaceState(null, "", url);
                  };
                  window.activate = activate;
                  const sourceColor = node => node.color && node.color.fill ? node.color.fill : "#F57826";
                  const sourceColorForKey = key => {
                    const paint = graphData.source_set_colors && graphData.source_set_colors[key];
                    return paint && paint.fill ? paint.fill : "#8b5cf6";
                  };
                  const nodeSize = node => 5 + Math.min(12, Math.sqrt(Number(node.record_count || 1)) * 3);
                  const linkStyle = type => TYPES[type] || TYPES.possibly_related;
                  const buildSourceLegend = nodes => {
                    const legend = byId("sourceLegend");
                    legend.replaceChildren();
                    const combinations = [...new Set(nodes.map(node => node.source_set_key).filter(key => key && key.includes("|")))].sort();
                    const sourceCounts = new Map();
                    const sourceSetCounts = new Map();
                    nodes.forEach(node => {
                      sourceSetCounts.set(node.source_set_key, (sourceSetCounts.get(node.source_set_key) || 0) + 1);
                      (node.data_sources || []).forEach(source => sourceCounts.set(source, (sourceCounts.get(source) || 0) + 1));
                    });
                    legend.append(make("div", "legend-heading", "Entities spanning multiple sources use distinct colors"));
                    const addFilter = (label, key, fill, kind, count) => {
                      const button = make("button");
                      button.type = "button";
                      const activeFilters = kind === "set" ? selectedSourceSets : selectedSources;
                      button.setAttribute("aria-pressed", String(
                        activeFilters.has(key) || (selectedSourceSets.size === 0 && selectedSources.size === 0)));
                      const swatch = make("span", "swatch");
                      swatch.style.backgroundColor = fill;
                      button.append(swatch, make("span", "", label), make("span", "muted", "(" + number(count) + ")"));
                      button.title = key;
                      button.addEventListener("click", () => {
                        const set = kind === "set" ? selectedSourceSets : selectedSources;
                        if (set.has(key)) set.delete(key); else set.add(key);
                        allSourceFilters = selectedSources.size === 0 && selectedSourceSets.size === 0;
                        button.setAttribute("aria-pressed", String(allSourceFilters || set.has(key)));
                        drawGraph();
                      });
                      legend.append(button);
                    };
                    const comboLabels = uniqueLabels(combinations.map(key => key.split("|").join(" + ")), 34);
                    combinations.forEach((key, index) => {
                      addFilter("Combination: " + comboLabels[index], key, sourceColorForKey(key), "set",
                        Number(sourceSetCounts.get(key) || 0));
                    });
                    if (!combinations.length) legend.append(make("span", "muted", "No multi-source combinations are present."));
                    legend.append(make("div", "legend-heading", "Entities per data source; cross-source entities appear in each source count"));
                    const sources = [...sourceCounts.keys()].sort();
                    const sourceLabels = uniqueLabels(sources, 24);
                    sources.forEach((source, index) => {
                      addFilter(sourceLabels[index], source, sourceColorForKey(source), "source",
                        Number(sourceCounts.get(source) || 0));
                    });
                    return {combinationRows: combinations.length, combinations};
                  };
                  const buildRelationshipLegend = edges => {
                    const wrapper = byId("relationshipLegendWrap");
                    const legend = byId("relationshipLegend");
                    legend.replaceChildren();
                    const types = [...new Set(edges.map(edge => edge.relationship_type))];
                    wrapper.hidden = !relationshipMode || !types.length;
                    types.forEach(type => {
                      const style = linkStyle(type);
                      const button = make("button");
                      button.type = "button";
                      button.setAttribute("aria-pressed", String(
                        selectedRelationships.size === 0 || selectedRelationships.has(type)));
                      const line = make("span", "line-swatch" + (style.dash ? (type === "ambiguous" ? " dotted" : " dashed") : ""));
                      line.style.color = style.color;
                      line.style.borderTopColor = style.color;
                      button.append(line, make("span", "", style.label));
                      button.addEventListener("click", () => {
                        if (selectedRelationships.has(type)) selectedRelationships.delete(type);
                        else selectedRelationships.add(type);
                        allRelationshipFilters = selectedRelationships.size === 0;
                        button.setAttribute("aria-pressed", String(allRelationshipFilters || selectedRelationships.has(type)));
                        drawGraph();
                      });
                      legend.append(button);
                    });
                  };
                  const graphNoteText = (allNodes, shownNodes, relatedIds, graph) => {
                    const total = Number(graph.total || 0);
                    const shown = Number(graph.nodes.length || 0);
                    const relatedTotal = Number(graph.related_total || 0);
                    const shownRelated = shownNodes.filter(node => relatedIds.has(String(node.id))).length;
                    if (relationshipMode && graph.capped && shownRelated === 0) {
                      return "None of the " + number(relatedTotal) + " entities with relationships are among the " + number(shown) + " shown.";
                    }
                    if (relationshipMode && graph.capped) {
                      return "Showing " + number(shownRelated) + " of the " + number(relatedTotal) + " entities that have relationships; the graph is capped at " + number(shown) + " of " + number(total) + " entities.";
                    }
                    if (relationshipMode) {
                      return "Showing " + number(shownRelated) + " entities that have relationships, out of " + number(relatedTotal) + " in the full population.";
                    }
                    if (graph.capped) {
                      return "Showing " + number(shown) + " of " + number(total) + " entities - the graph is capped; entities spanning the most sources are kept first.";
                    }
                    return "Showing all " + number(total) + " entities.";
                  };
                  const drawGraph = () => {
                    document.documentElement.removeAttribute("data-graph-settled");
                    graphData = data.graph;
                    const svg = d3.select("#graphSvg");
                    const element = byId("graphSvg");
                    const width = Math.max(320, element.clientWidth);
                    const height = Math.max(360, element.clientHeight);
                    if (!graphLabelsChosen) {
                      const labelLimit = captureMode ? captureLabelLimit : interactiveLabelLimit;
                      nodeLabelsOn = Number(graphData.total) <= labelLimit;
                      edgeLabelsOn = Number(graphData.total) <= labelLimit;
                      graphLabelsChosen = true;
                    }
                    svg.attr("viewBox", "0 0 " + width + " " + height);
                    svg.selectAll("*").remove();
                    const allNodes = graphData.nodes || [];
                    const edgeRows = graphData.edges || [];
                    const relatedIds = new Set();
                    edgeRows.forEach(edge => {
                      relatedIds.add(String(edge.source_entity_id));
                      relatedIds.add(String(edge.target_entity_id));
                    });
                    byId("relationshipControl").hidden = Number(data.stats.relationships_total) === 0;
                    if (Number(graphData.total) > 400 && relatedIds.size > 0 && !graphModeChosen) {
                      relationshipMode = true;
                      byId("relationshipMode").checked = true;
                    }
                    const selectedNodes = relationshipMode
                      ? allNodes.filter(node => relatedIds.has(String(node.entity_id)))
                      : allNodes;
                    const selectedIds = new Set(selectedNodes.map(node => String(node.entity_id)));
                    const selectedEdges = relationshipMode
                      ? edgeRows.filter(edge => selectedIds.has(String(edge.source_entity_id))
                          && selectedIds.has(String(edge.target_entity_id))
                          && (allRelationshipFilters || selectedRelationships.size === 0 || selectedRelationships.has(edge.relationship_type)))
                      : edgeRows.filter(edge => selectedIds.has(String(edge.source_entity_id))
                          && selectedIds.has(String(edge.target_entity_id)));
                    const nodes = selectedNodes.filter(node => {
                      if (!selectedSourceSets.size && !selectedSources.size) return true;
                      const sourceSetMatch = selectedSourceSets.has(node.source_set_key);
                      const sourceMatch = (node.data_sources || []).some(source => selectedSources.has(source));
                      return sourceSetMatch || sourceMatch;
                    }).map(node => ({...node, id: node.entity_id, originalId: node.entity_id}));
                    const visibleIds = new Set(nodes.map(node => String(node.id)));
                    const links = selectedEdges
                      .filter(edge => visibleIds.has(String(edge.source_entity_id)) && visibleIds.has(String(edge.target_entity_id)))
                      .map(edge => ({...edge, source: edge.source_entity_id, target: edge.target_entity_id}));
                    const captureLabelsHidden = captureMode && nodes.length > captureLabelLimit;
                    const labelHidden = captureLabelsHidden || (nodes.length > interactiveLabelLimit && !nodeLabelsOn);
                    const edgeLabelHidden = captureLabelsHidden || (nodes.length > interactiveLabelLimit && !edgeLabelsOn);
                    byId("nodeLabels").checked = nodeLabelsOn;
                    byId("edgeLabels").checked = edgeLabelsOn;
                    byId("nodeLabels").disabled = captureLabelsHidden;
                    byId("edgeLabels").disabled = captureLabelsHidden;
                    const labelNote = captureLabelsHidden
                      ? "Labels are hidden in capture mode above " + number(captureLabelLimit) + " visible entities to keep the graph readable."
                      : nodes.length > interactiveLabelLimit && (labelHidden || edgeLabelHidden)
                        ? "Node and edge labels are off by default above " + number(interactiveLabelLimit) + " entities. Turn them on when you need exact labels."
                        : "";
                    const note = graphNoteText(allNodes, nodes, relatedIds, graphData);
                    const legendState = buildSourceLegend(nodes);
                    byId("sourceLegendWrap").hidden = relationshipMode;
                    const encoding = graphData.encoding_check || {};
                    const combinationKeys = encoding.combination_keys || [];
                    const fullGraphView = !relationshipMode && selectedSourceSets.size === 0
                      && selectedSources.size === 0 && nodes.length === allNodes.length;
                    const visibleCombinations = legendState.combinations;
                    const sameCombinations = visibleCombinations.length === combinationKeys.length
                      && visibleCombinations.every((key, index) => key === combinationKeys[index]);
                    const nodesUseSetColors = nodes.every(node => {
                      const paint = graphData.source_set_colors && graphData.source_set_colors[node.source_set_key];
                      return paint && node.color
                        && paint.fill === node.color.fill
                        && paint.stroke === node.color.stroke
                        && paint.stroke_width === node.color.stroke_width;
                    });
                    const encodingCheck = !visibleCombinations.length
                      ? "Source-set encoding self-check: not exercised (no cross-source entities are in view)."
                      : !fullGraphView
                        ? "Source-set encoding self-check: restore the full graph view to inspect all source combinations."
                        : legendState.combinationRows === combinationKeys.length && sameCombinations
                            && encoding.status === "ok" && nodesUseSetColors
                          ? "Source-set encoding self-check: passed (" + number(legendState.combinationRows) + " combination rows)."
                          : "Source-set encoding self-check: mismatch; stop before capture and inspect the source legend and graph encoding.";
                    byId("graphNote").textContent = [note, labelNote, encodingCheck].filter(Boolean).join(" ");
                    buildRelationshipLegend(links);
                    if (!nodes.length) {
                      svg.append("text").attr("x", width / 2).attr("y", height / 2).attr("text-anchor", "middle")
                        .attr("fill", "#4A4640").text("No entities are available in this graph view.");
                      if (graphSimulation) graphSimulation.stop();
                      graphSimulation = null;
                      document.documentElement.setAttribute("data-graph-settled", "1");
                      return;
                    }
                    if (graphSimulation) graphSimulation.stop();
                    const root = svg.append("g");
                    graphZoom = d3.zoom().scaleExtent([0.25, 4]).on("zoom", event => root.attr("transform", event.transform));
                    svg.call(graphZoom);
                    const linkLayer = root.append("g").attr("class", "links");
                    const edgeLabelLayer = root.append("g").attr("class", "edge-labels");
                    const nodeLayer = root.append("g").attr("class", "nodes");
                    const labelLayer = root.append("g").attr("class", "node-labels");
                    const linksSelection = linkLayer.selectAll("line").data(links).join("line")
                      .attr("stroke", edge => linkStyle(edge.relationship_type).color)
                      .attr("stroke-dasharray", edge => linkStyle(edge.relationship_type).dash)
                      .attr("stroke-opacity", 0.62)
                      .attr("stroke-width", 1.6);
                    linksSelection.append("title").text(edge => linkStyle(edge.relationship_type).label + (edge.match_key ? " · " + edge.match_key : ""));
                    if (!edgeLabelHidden) {
                      edgeLabelLayer.selectAll("text").data(links).join("text")
                        .attr("fill", "#4A4640").attr("font-size", 9).attr("text-anchor", "middle")
                        .text(edge => edge.match_key || linkStyle(edge.relationship_type).label);
                    }
                    const nodeSelection = nodeLayer.selectAll("circle").data(nodes, node => node.id).join("circle")
                      .attr("r", nodeSize)
                      .attr("fill", sourceColor)
                      .attr("stroke", node => node.color && node.color.stroke_width ? node.color.stroke : "#fff")
                      .attr("stroke-width", node => node.color && node.color.stroke_width ? node.color.stroke_width : 1)
                      .call(d3.drag()
                        .on("start", (event, node) => { if (!event.active) graphSimulation.alphaTarget(0.3).restart(); node.fx = node.x; node.fy = node.y; })
                        .on("drag", (event, node) => { node.fx = event.x; node.fy = event.y; })
                        .on("end", (event, node) => { if (!event.active) graphSimulation.alphaTarget(0); node.fx = null; node.fy = null; }));
                    nodeSelection.append("title").text(node => (node.entity_name || "Entity " + node.id) + " · " + number(node.record_count) + " records · " + (node.data_sources || []).join(", "));
                    nodeSelection.on("click", (event, node) => {
                      const body = make("div");
                      body.append(make("p", "entity-meta", "Entity " + node.entity_id + " · " + number(node.record_count) + " records · " + (node.data_sources || []).join(", ")));
                      body.append(actionButtons(node.entity_id));
                      openModal(node.entity_name || ("Entity " + node.entity_id), body);
                    });
                    if (!labelHidden) {
                      const labels = uniqueLabels(nodes.map(node => node.entity_name || ("Entity " + node.id)), 22);
                      labelLayer.selectAll("text").data(nodes, node => node.id).join("text")
                        .attr("font-size", 10).attr("font-weight", 650).attr("fill", "#18160F")
                        .attr("text-anchor", "middle").attr("dy", node => nodeSize(node) + 12)
                        .text((node, index) => labels[index])
                        .append("title").text(node => node.entity_name || ("Entity " + node.id));
                    }
                    const collisionPad = labelHidden ? 4 : 30;
                    graphSimulation = d3.forceSimulation(nodes)
                      .force("link", d3.forceLink(links).id(node => node.id).distance(78).strength(0.22))
                      .force("charge", d3.forceManyBody().strength(-180))
                      .force("center", d3.forceCenter(width / 2, height / 2))
                      .force("collide", d3.forceCollide(node => nodeSize(node) + collisionPad).iterations(2))
                      ;
                    const updateGraph = () => {
                        linksSelection.attr("x1", edge => edge.source.x).attr("y1", edge => edge.source.y)
                          .attr("x2", edge => edge.target.x).attr("y2", edge => edge.target.y);
                        nodeSelection.attr("cx", node => node.x).attr("cy", node => node.y);
                        labelLayer.selectAll("text").attr("x", node => node.x).attr("y", node => node.y);
                        edgeLabelLayer.selectAll("text")
                          .attr("x", edge => (edge.source.x + edge.target.x) / 2)
                          .attr("y", edge => (edge.source.y + edge.target.y) / 2);
                    };
                    const fitGraph = () => {
                      const minX = Math.min(...nodes.map(node => node.x));
                      const maxX = Math.max(...nodes.map(node => node.x));
                      const minY = Math.min(...nodes.map(node => node.y));
                      const maxY = Math.max(...nodes.map(node => node.y));
                      const padding = 34;
                      const scale = Math.min(
                        1.4,
                        (width - 2 * padding) / Math.max(1, maxX - minX),
                        (height - 2 * padding) / Math.max(1, maxY - minY));
                      const transform = d3.zoomIdentity
                        .translate(width / 2 - ((minX + maxX) / 2) * scale, height / 2 - ((minY + maxY) / 2) * scale)
                        .scale(scale);
                      svg.call(graphZoom.transform, transform);
                    };
                    if (captureMode) {
                      graphSimulation.stop();
                      let ticks = 0;
                      while (graphSimulation.alpha() > graphSimulation.alphaMin() && ticks < 1200) {
                        graphSimulation.tick();
                        ticks++;
                      }
                      updateGraph();
                      if (graphSimulation.alpha() <= graphSimulation.alphaMin()) {
                        fitGraph();
                        document.documentElement.setAttribute("data-graph-settled", "1");
                      } else {
                        byId("graphNote").textContent += " Capture layout did not settle.";
                      }
                    } else {
                      graphSimulation.on("tick", updateGraph).on("end", () => {
                        updateGraph();
                        document.documentElement.setAttribute("data-graph-settled", "1");
                      });
                    }
                  };
                  const drawStats = () => {
                    const histogram = data.stats.histogram || {};
                    const buckets = ["1", "2", "3", "4+"];
                    const maxValue = Math.max(1, ...buckets.map(key => Number(histogram[key] || 0)));
                    const container = byId("histogram");
                    container.replaceChildren();
                    buckets.forEach(bucket => {
                      const count = Number(histogram[bucket] || 0);
                      const row = make("div", "bar-row");
                      row.append(make("div", "", bucket + " record" + (bucket === "1" ? "" : "s")));
                      const button = make("button", "bar-button");
                      button.type = "button";
                      button.style.width = Math.max(2, count / maxValue * 100) + "%";
                      button.title = count + " entities with " + bucket + " record" + (bucket === "1" ? "" : "s");
                      button.setAttribute("aria-label", button.title);
                      button.addEventListener("click", () => showEntityList("Entities with " + bucket + " records", data.stats.bucket_entities[bucket] || []));
                      row.append(button, make("div", "bar-value", number(count)));
                      container.append(row);
                    });
                    entityList(data.stats.sample_entities || [], byId("largestEntities"));
                  };
                  const showEntityList = (title, entities) => {
                    const body = make("div", "entity-list");
                    entityList(entities, body);
                    openModal(title, body);
                  };
                  const drawMatchKeys = () => {
                    const payload = data.matchkeys;
                    const list = payload.match_keys || [];
                    const container = byId("matchKeysChart");
                    container.replaceChildren();
                    byId("matchKeyNote").hidden = !payload.capped && !payload.entities_capped;
                    byId("matchKeyNote").textContent = payload.capped
                      ? "Showing the top " + number(list.length) + " of " + number(payload.distinct) + " match keys."
                      : payload.entities_capped ? "Entity drill-down lists are capped at 200 items per match key." : "";
                    const maxValue = Math.max(1, ...list.map(item => Number(item.count || 0)));
                    const labels = uniqueLabels(list.map(item => item.match_key), 28);
                    const longestKey = Math.max(0, ...list.map(item => safeText(item.match_key).length));
                    const labelGutter = Math.min(360, Math.max(150, longestKey * 7.2));
                    list.forEach((item, index) => {
                      const row = make("div", "bar-row");
                      row.style.gridTemplateColumns = labelGutter + "px 1fr 54px";
                      const label = make("button", "clickable bar-label", labels[index]);
                      label.title = item.match_key;
                      label.addEventListener("click", () => showEntityList("Entities carrying " + item.match_key, payload.match_key_entities[item.match_key] || []));
                      row.append(label);
                      const bar = make("button", "bar-button");
                      bar.type = "button";
                      bar.style.width = Math.max(2, Number(item.count) / maxValue * 100) + "%";
                      bar.title = item.match_key + " · " + number(item.count);
                      bar.addEventListener("click", () => showEntityList("Entities carrying " + item.match_key, payload.match_key_entities[item.match_key] || []));
                      row.append(bar, make("div", "bar-value", number(item.count)));
                      container.append(row);
                    });
                    if (!list.length) container.append(make("div", "empty", "No multi-record match keys were returned."));
                  };
                  const drawFeatures = () => {
                    const payload = data.features;
                    const note = payload.sampled === 0
                      ? "No feature-score samples were available. The feature tab is based on up to 40 multi-record entities."
                      : "Aggregated " + number(payload.sampled) + " of " + number(payload.multi_record_total) + " multi-record entities" + (payload.capped ? " (sample cap: 40)." : ".");
                    byId("featureNote").textContent = note;
                    const container = byId("featureCharts");
                    container.replaceChildren();
                    (payload.features || []).forEach(feature => {
                      const card = make("div", "score-card");
                      card.append(make("h3", "", feature.feature));
                      const buckets = Object.entries(feature.buckets || {}).sort((a, b) => b[1] - a[1]);
                      const maxValue = Math.max(1, ...buckets.map(entry => Number(entry[1])));
                      buckets.forEach(([label, count]) => {
                        const row = make("div", "bar-row");
                        row.append(make("div", "", label));
                        const bar = make("div", "bar-button");
                        bar.style.width = Math.max(2, Number(count) / maxValue * 100) + "%";
                        bar.setAttribute("role", "img");
                        bar.setAttribute("aria-label", label + ": " + count);
                        row.append(bar, make("div", "bar-value", number(count)));
                        card.append(row);
                      });
                      container.append(card);
                    });
                    if (!(payload.features || []).length) container.append(make("div", "empty", "No feature-score distribution is available."));
                  };
                  const drawOverlap = () => {
                    const payload = data.overlap;
                    const tableWrap = byId("overlapTable");
                    tableWrap.replaceChildren();
                    byId("overlapNote").hidden = !payload.cell_capped;
                    byId("overlapNote").textContent = payload.cell_capped ? "Some cell drill-down lists are capped at 200 entities; counts remain exact." : "";
                    const table = make("table");
                    const head = make("tr");
                    head.append(make("th", "", "Data source"));
                    payload.sources.forEach(source => {
                      const th = make("th", "", fitLabel(source, 22));
                      th.title = source;
                      head.append(th);
                    });
                    table.append(head);
                    const maxValue = Math.max(1, ...payload.matrix.flat());
                    payload.sources.forEach((source, i) => {
                      const row = make("tr");
                      const label = make("th", "", source);
                      row.append(label);
                      payload.sources.forEach((target, j) => {
                        const td = make("td");
                        const button = make("button", "heat", number(payload.matrix[i][j]));
                        const key = Math.min(i, j) + "," + Math.max(i, j);
                        button.title = source + " and " + target + ": " + number(payload.matrix[i][j]) + " entities";
                        button.style.backgroundColor = "rgba(245,120,38," + (0.08 + 0.62 * Number(payload.matrix[i][j]) / maxValue) + ")";
                        button.addEventListener("click", () => showEntityList(source === target ? "Entities in " + source : "Entities in " + source + " and " + target, payload.cell_entities[key] || []));
                        td.append(button);
                        row.append(td);
                      });
                      table.append(row);
                    });
                    tableWrap.append(table);
                  };
                  const drawChips = () => {
                    const container = byId("searchChips");
                    container.replaceChildren();
                    if (window.__SNAPSHOT__) return;
                    (data.examples || []).forEach(example => {
                      const button = make("button", "chip", example.query);
                      button.type = "button";
                      button.title = "Pre-verified example with " + number(example.result_count) + " result(s)";
                      button.addEventListener("click", () => {
                        byId("searchInput").value = example.query;
                        runSearch(example.query);
                      });
                      container.append(button);
                    });
                  };
                  const runSearch = async query => {
                    byId("searchStatus").replaceChildren();
                    byId("searchResults").replaceChildren();
                    if (window.__SNAPSHOT__) {
                      byId("searchStatus").append(make("p", "note notice", "Live search is available while the server is running."));
                      return;
                    }
                    const status = make("p", "muted", "Searching NAME_FULL, then NAME_ORG if needed…");
                    byId("searchStatus").append(status);
                    try {
                      const response = await api("/api/search?q=" + encodeURIComponent(query));
                      status.textContent = "Attributes tried: " + (response.attributes_tried || []).join(", ");
                      const failures = response.failed_attributes || [];
                      if (failures.length) {
                        const note = make("p", "note warning", "Some search attempts failed: " + failures.map(item => item.attribute).join(", "));
                        byId("searchStatus").append(note);
                      }
                      if (!response.results.length) {
                        byId("searchResults").append(make("div", "empty", "No entity matched the attempted NAME_FULL then NAME_ORG search for this query. An empty result does not prove the record is absent."));
                        return;
                      }
                      const body = make("div", "entity-list");
                      response.results.forEach(result => {
                        const row = make("div", "entity-row");
                        const info = make("div");
                        info.append(make("div", "entity-name", result.entity_name || ("Entity " + result.entity_id)));
                        info.append(make("div", "entity-meta", "Entity " + result.entity_id + " · " + number(result.record_count) + " records · " + (result.data_sources || []).join(", ")));
                        if (result.enrichment_error) info.append(make("p", "note warning", result.enrichment_error));
                        row.append(info, actionButtons(result.entity_id));
                        body.append(row);
                        if (result.match_keys) {
                          const detail = make("div", "entity-meta");
                          detail.textContent = "Match key: " + (result.match_keys.entity_level || "entity-level key unavailable")
                            + " · Rules: " + (result.resolution_rules || []).map(rule => rule.rule).filter(Boolean).join(", ");
                          body.append(detail);
                        }
                      });
                      byId("searchResults").append(body);
                    } catch (error) {
                      status.className = "note warning";
                      status.textContent = "Search failed: " + error.message;
                    }
                  };
                  const bindControls = () => {
                    byId("relationshipMode").addEventListener("change", event => {
                      relationshipMode = event.target.checked;
                      graphModeChosen = true;
                      drawGraph();
                    });
                    byId("nodeLabels").addEventListener("change", event => { nodeLabelsOn = event.target.checked; graphLabelsChosen = true; drawGraph(); });
                    byId("edgeLabels").addEventListener("change", event => { edgeLabelsOn = event.target.checked; graphLabelsChosen = true; drawGraph(); });
                    byId("resetGraph").addEventListener("click", () => {
                      selectedSources.clear(); selectedSourceSets.clear(); selectedRelationships.clear();
                      allSourceFilters = true; allRelationshipFilters = true;
                      if (graphZoom) d3.select("#graphSvg").transition().duration(250).call(graphZoom.transform, d3.zoomIdentity);
                      drawGraph();
                    });
                    byId("searchForm").addEventListener("submit", event => {
                      event.preventDefault();
                      const value = byId("searchInput").value.trim();
                      if (value) runSearch(value);
                    });
                    byId("showMerged").addEventListener("click", () => showEntityList("All merged entities", data.merges.entities || []));
                    byId("dialogClose").addEventListener("click", () => byId("detailDialog").close());
                  };
                  const load = async () => {
                    if (window.__SNAPSHOT__) return window.__DATA__;
                    await api("/api/health?nonce=" + encodeURIComponent(window.__NONCE__));
                    const [stats, graph, merges, overlap, matchkeys, features, examplesPayload] = await Promise.all([
                      api("/api/stats"), api("/api/graph"), api("/api/merges"),
                      api("/api/overlap"), api("/api/matchkeys"), api("/api/features"), api("/api/examples")
                    ]);
                    return {stats, graph, merges, overlap, matchkeys, features, records: {}, examples: examplesPayload.examples || []};
                  };
                  const init = async () => {
                    bindControls();
                    try {
                      data = await load();
                      buildSummary(data.stats);
                      buildNav(data.stats);
                      drawChips();
                      byId("relationshipControl").hidden = Number(data.stats.relationships_total) === 0;
                      const params = new URLSearchParams(window.location.search);
                      const requestedTab = params.get("tab");
                      const query = params.get("q");
                      if (query) {
                        byId("searchInput").value = query;
                        if (!requestedTab) activate("probe");
                      }
                      if (requestedTab && tabs.includes(requestedTab) && !byId("navbtn-" + requestedTab).hidden) {
                        activate(requestedTab);
                      } else if (activeTab == null) {
                        activate("graph");
                      }
                      if (query) runSearch(query);
                    } catch (error) {
                      document.querySelector("main").prepend(make("p", "note warning", "Visualization data could not be loaded: " + error.message));
                    }
                  };
                  init();
                })();
              </script>
            </body>
            </html>
            """;

    private TruthSetPage() {}

    static String template() {
        return TEMPLATE;
    }

    static String render(String template,
                         String d3,
                         String title,
                         String dataset,
                         JsonObject data,
                 boolean snapshot,
                 String nonce) {
        Objects.requireNonNull(template);
        String inlineD3 = d3.replace("</script", "<\\/script");
        String json = snapshot ? data.toString() : "null";
        json = json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
        return template
                .replace("@@TITLE@@", escapeHtml(title))
                .replace("@@DATASET@@", escapeHtml(dataset))
                .replace("@@D3@@", inlineD3)
                .replace("@@DATA@@", json)
                .replace("@@SNAPSHOT@@", Boolean.toString(snapshot))
                .replace("@@NONCE@@", escapeJs(nonce));
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("<", "\\u003c").replace(">", "\\u003e");
    }
}
