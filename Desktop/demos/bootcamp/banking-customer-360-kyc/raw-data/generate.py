"""Split the Senzing truth set into banking feeds.

customers.jsonl -> core_cif_extract.jsonl (CORE_BANKING) + card_cardholder_master.jsonl (CARD_SERVICES)
reference.jsonl -> credit_header.jsonl (CREDIT_BUREAU) + business_registry.jsonl (BUSINESS_REGISTRY)
watchlist.jsonl -> screening_list.jsonl (WATCHLIST, with LIST_TYPE)

RECORD_IDs are unchanged. Deterministic (fixed seed).
usage: generate.py <truthset_demo_dir> <out_dir>
"""
import csv, json, random, sys, zlib
from collections import defaultdict
from datetime import date, datetime

src, out = sys.argv[1], sys.argv[2]
rng = random.Random(20261111)

key = {(r["DATA_SOURCE"], r["RECORD_ID"]): r["CLUSTER_ID"] for r in csv.DictReader(open(f"{src}/actual_truthset_key.csv"))}
customers = [json.loads(l) for l in open(f"{src}/customers.jsonl")]
reference = [json.loads(l) for l in open(f"{src}/reference.jsonl")]
watchlist = [json.loads(l) for l in open(f"{src}/watchlist.jsonl")]


def rtype(r):
    for f in r.get("FEATURES", []):
        if "RECORD_TYPE" in f:
            return f["RECORD_TYPE"]
    return "ORGANIZATION" if any("NAME_ORG" in f for f in r.get("FEATURES", [])) else "PERSON"


def h(s):
    return zlib.crc32(s.encode()) % 100


# ---- decide which feed each customer record goes to ----
clusters = defaultdict(list)
for r in customers:
    clusters[key[("CUSTOMERS", r["RECORD_ID"])]].append(r)

feed = {}
for cid, recs in clusters.items():
    recs.sort(key=lambda r: r["RECORD_ID"])
    if len(recs) == 1:
        r = recs[0]
        if rtype(r) == "ORGANIZATION":
            feed[r["RECORD_ID"]] = "CARD_SERVICES" if h(r["RECORD_ID"]) < 70 else "CORE_BANKING"
        else:
            feed[r["RECORD_ID"]] = "CORE_BANKING" if h(r["RECORD_ID"]) < 60 else "CARD_SERVICES"
    else:
        # first two land in different systems; the rest mostly in deposits (a second account)
        for i, r in enumerate(recs):
            if i == 0:
                feed[r["RECORD_ID"]] = "CORE_BANKING"
            elif i == 1:
                feed[r["RECORD_ID"]] = "CARD_SERVICES"
            else:
                feed[r["RECORD_ID"]] = "CORE_BANKING" if i % 2 == 0 else "CARD_SERVICES"


def open_date(r):
    d = r.get("DATE")
    if d:
        try:
            return datetime.strptime(d, "%m/%d/%y").date().isoformat()
        except ValueError:
            pass
    return date(rng.randint(2008, 2025), rng.randint(1, 12), rng.randint(1, 28)).isoformat()


BRANCHES = ["Main Street", "Riverside", "North Park", "Westgate", "Lakeview", "Downtown"]
TIER = {"Platinum": "Platinum", "Gold": "Gold", "Silver": "Silver", "Bronze": "Bronze"}


def deposits(r):
    org = rtype(r) == "ORGANIZATION"
    status = {"Active": "Open", "Inactive": "Dormant", "Terminated": "Closed"}.get(r.get("STATUS"), "Open")
    product = rng.choice(["DDA", "DDA", "MMDA"]) if org else rng.choices(["DDA", "SAV", "MMDA", "CD"], [5, 3, 1, 1])[0]
    bal = 0 if status == "Closed" else round(rng.lognormvariate(9.3 if not org else 11, 1.1), 2)
    p = {
        "ACCOUNT_NUMBER": f"{rng.randint(10**9, 10**10 - 1)}",
        "PRODUCT_TYPE": product,
        "OWNERSHIP_TYPE": "Business" if org else rng.choices(["Individual", "Joint"], [4, 1])[0],
        "ACCOUNT_STATUS": status,
        "OPEN_DATE": open_date(r),
        "CURRENT_BALANCE": bal,
        "BRANCH": rng.choice(BRANCHES),
    }
    if r.get("CATEGORY") in TIER:
        p["RELATIONSHIP_TIER"] = TIER[r["CATEGORY"]]
    return p


def cards(r):
    org = rtype(r) == "ORGANIZATION"
    status = {"Active": "Open", "Inactive": "Closed", "Terminated": "Charged Off"}.get(r.get("STATUS"), "Open")
    role = "Primary"
    for f in r.get("FEATURES", []):
        if f.get("REL_POINTER_ROLE") in ("Spouse", "Carer"):
            role = "Authorized User"
    limit = rng.choice([10000, 25000, 50000]) if org else rng.choice([1000, 2500, 5000, 7500, 10000, 15000, 20000])
    bal = 0 if status == "Closed" else round(rng.uniform(0.05, 0.85) * limit, 2)
    if status == "Charged Off":
        bal, dpd = round(limit * 0.97, 2), "90+ DPD"
    else:
        dpd = "Current" if status == "Closed" else rng.choices(["Current", "30 DPD", "60 DPD", "90+ DPD"], [90, 5, 3, 2])[0]
    return {
        "CARD_ACCOUNT_ID": f"CA{rng.randint(10**7, 10**8 - 1)}",
        "PAN_LAST4": f"{rng.randint(0, 9999):04d}",
        "CARDHOLDER_ROLE": role,
        "PRODUCT": "Business" if org else rng.choices(["Rewards", "Cash Back", "Secured"], [5, 4, 1])[0],
        "CREDIT_LIMIT": limit,
        "CURRENT_BALANCE": bal,
        "OPEN_DATE": open_date(r),
        "ACCOUNT_STATUS": status,
        "DELINQUENCY_BUCKET": dpd,
    }


def strip(r, drop=("AMOUNT", "DATE", "STATUS", "CATEGORY")):
    return {k: v for k, v in r.items() if k not in drop}


files = {
    "core_cif_extract.jsonl": [],
    "card_cardholder_master.jsonl": [],
    "credit_header.jsonl": [],
    "business_registry.jsonl": [],
    "screening_list.jsonl": [],
}
remap = []  # private answer key: new data source per record

for r in customers:
    ds = feed[r["RECORD_ID"]]
    base = strip(r)
    pay = deposits(r) if ds == "CORE_BANKING" else cards(r)
    n = {"DATA_SOURCE": ds, "RECORD_ID": r["RECORD_ID"], **{k: v for k, v in base.items() if k not in ("DATA_SOURCE", "RECORD_ID")}, **pay}
    files["core_cif_extract.jsonl" if ds == "CORE_BANKING" else "card_cardholder_master.jsonl"].append(n)
    remap.append((key[("CUSTOMERS", r["RECORD_ID"])], ds, r["RECORD_ID"]))

for r in reference:
    bureau = r.get("CATEGORY") == "Credit Header"
    ds = "CREDIT_BUREAU" if bureau else "BUSINESS_REGISTRY"
    n = {"DATA_SOURCE": ds, "RECORD_ID": r["RECORD_ID"], **{k: v for k, v in strip(r, ("DATA_SOURCE", "RECORD_ID", "STATUS", "CATEGORY", "DATE")).items()}}
    if bureau:
        n["FILE_STATUS"] = r.get("STATUS")
    else:
        n["REGISTRY_ROLE"] = r.get("CATEGORY")
        n["REGISTRY_STATUS"] = r.get("STATUS")
        if r.get("DATE"):
            n["INCORPORATED"] = r["DATE"]
    files["credit_header.jsonl" if bureau else "business_registry.jsonl"].append(n)
    remap.append((key[("REFERENCE", r["RECORD_ID"])], ds, r["RECORD_ID"]))

LIST = {"Fraud": "Internal Fraud", "Sanctioned": "Sanctions", "PEP": "PEP"}
for r in watchlist:
    n = {"DATA_SOURCE": "WATCHLIST", "RECORD_ID": r["RECORD_ID"], **strip(r, ("DATA_SOURCE", "RECORD_ID", "STATUS", "CATEGORY", "DATE")),
         "LIST_TYPE": LIST.get(r.get("CATEGORY"), r.get("CATEGORY")), "LIST_STATUS": r.get("STATUS"), "LISTED_DATE": r.get("DATE")}
    files["screening_list.jsonl"].append(n)
    remap.append((key[("WATCHLIST", r["RECORD_ID"])], "WATCHLIST", r["RECORD_ID"]))

for name, rows in files.items():
    with open(f"{out}/{name}", "w") as fh:
        for row in rows:
            fh.write(json.dumps(row) + "\n")
    print(f"{name:32s} {len(rows):4d}")

with open(f"{out}/banking_truthset_key.csv", "w", newline="") as fh:
    w = csv.writer(fh)
    w.writerow(["CLUSTER_ID", "DATA_SOURCE", "RECORD_ID"])
    w.writerows(remap)

# split stats
both = sum(1 for recs in clusters.values() if len({feed[r["RECORD_ID"]] for r in recs}) == 2)
print("customer clusters:", len(clusters), " spanning both systems:", both)
