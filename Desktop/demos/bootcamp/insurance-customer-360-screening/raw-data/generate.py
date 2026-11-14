"""Split the Senzing truth set into insurance feeds.

customers.jsonl -> pc_policy_admin_extract.jsonl (PC_POLICY_ADMIN)
                 + life_annuity_admin_extract.jsonl (LIFE_ANNUITY_ADMIN)
reference.jsonl -> credit_header.jsonl (CREDIT_BUREAU) + business_registry.jsonl (BUSINESS_REGISTRY)
watchlist.jsonl -> siu_cases.jsonl (SIU_CASES, the fraud records) + sanctions_pep.jsonl (SANCTIONS_PEP)

RECORD_IDs are unchanged. Deterministic (fixed seed).
Writes two private keys: insurance_truthset_key.csv (from actual_truthset_key.csv)
and insurance_alternate_key.csv (from alternate_truthset_key.csv, the one sz_audit uses in the demo).
usage: generate.py <truthset_demo_dir> <out_dir>
"""
import csv, json, random, sys, zlib
from collections import defaultdict
from datetime import date, datetime, timedelta

src, out = sys.argv[1], sys.argv[2]
rng = random.Random(20261111)

key = {(r["DATA_SOURCE"], r["RECORD_ID"]): r["CLUSTER_ID"] for r in csv.DictReader(open(f"{src}/actual_truthset_key.csv"))}
customers = [json.loads(l) for l in open(f"{src}/customers.jsonl")]
reference = [json.loads(l) for l in open(f"{src}/reference.jsonl")]
watchlist = [json.loads(l) for l in open(f"{src}/watchlist.jsonl")]

PC, LIFE = "PC_POLICY_ADMIN", "LIFE_ANNUITY_ADMIN"


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
            # commercial lines live in P&C; a few businesses only hold key-person life
            feed[r["RECORD_ID"]] = PC if h(r["RECORD_ID"]) < 80 else LIFE
        else:
            feed[r["RECORD_ID"]] = PC if h(r["RECORD_ID"]) < 60 else LIFE
    else:
        # first two land in different systems; the rest mostly in P&C (a second policy)
        for i, r in enumerate(recs):
            if i == 0:
                feed[r["RECORD_ID"]] = PC
            elif i == 1:
                feed[r["RECORD_ID"]] = LIFE
            else:
                feed[r["RECORD_ID"]] = PC if i % 2 == 0 else LIFE


def eff_date(r):
    d = r.get("DATE")
    if d:
        try:
            return datetime.strptime(d, "%m/%d/%y").date()
        except ValueError:
            pass
    return date(rng.randint(2008, 2025), rng.randint(1, 12), rng.randint(1, 28))


def rel_role(r):
    for f in r.get("FEATURES", []):
        if f.get("REL_POINTER_ROLE") in ("Spouse", "Carer"):
            return f["REL_POINTER_ROLE"]
    return None


PRODUCERS = ["AG-1042", "AG-1177", "AG-2210", "AG-2385", "AG-3019", "AG-3460"]


def pc_policy(r):
    org = rtype(r) == "ORGANIZATION"
    status = {"Active": "In Force", "Inactive": "Lapsed", "Terminated": "Cancelled"}.get(r.get("STATUS"), "In Force")
    if org:
        lob = rng.choices(["Businessowners", "Commercial Auto", "Workers Compensation", "General Liability"], [4, 3, 2, 2])[0]
        prem = round(rng.lognormvariate(8.6, 0.7), 2)
    else:
        lob = rng.choices(["Personal Auto", "Homeowners", "Renters", "Personal Umbrella"], [6, 4, 1, 1])[0]
        prem = round({"Personal Auto": rng.uniform(900, 3200), "Homeowners": rng.uniform(1100, 4200),
                      "Renters": rng.uniform(140, 320), "Personal Umbrella": rng.uniform(250, 600)}[lob], 2)
    eff = eff_date(r)
    p = {
        "POLICY_NUMBER": f"{'CP' if org else 'PP'}{rng.randint(10**7, 10**8 - 1)}",
        "LINE_OF_BUSINESS": lob,
        "INSURED_ROLE": "Named Insured" if org or not rel_role(r) else ("Additional Named Insured" if lob != "Personal Auto" else "Listed Driver"),
        "WRITTEN_PREMIUM": prem,
        "EFFECTIVE_DATE": eff.isoformat(),
        "EXPIRATION_DATE": (eff + timedelta(days=182 if lob == "Personal Auto" else 365)).isoformat(),
        "POLICY_STATUS": status,
        "PRODUCER_CODE": rng.choice(PRODUCERS),
    }
    if status == "Cancelled":
        p["CANCELLATION_REASON"] = "Material Misrepresentation"
    elif status == "Lapsed":
        p["CANCELLATION_REASON"] = "Non-Payment"
    if r.get("CATEGORY"):
        p["CUSTOMER_TIER"] = r["CATEGORY"]
    return p


def life_contract(r):
    org = rtype(r) == "ORGANIZATION"
    status = {"Active": "In Force", "Inactive": "Lapsed", "Terminated": "Rescinded"}.get(r.get("STATUS"), "In Force")
    if org:
        prod = "Key Person Term Life"
    else:
        prod = rng.choices(["Term Life", "Whole Life", "Universal Life", "Fixed Annuity", "Variable Annuity"], [5, 2, 2, 2, 1])[0]
    annuity = "Annuity" in prod
    role = "Owner"
    if rel_role(r) == "Spouse":
        role = "Beneficiary"
    elif rel_role(r) == "Carer":
        role = "Payor"
    p = {
        "CONTRACT_NUMBER": f"{'AN' if annuity else 'LF'}{rng.randint(10**7, 10**8 - 1)}",
        "PRODUCT_TYPE": prod,
        "PARTY_ROLE": role,
        "ISSUE_DATE": eff_date(r).isoformat(),
        "CONTRACT_STATUS": status,
    }
    if annuity:
        p["ACCOUNT_VALUE"] = round(rng.lognormvariate(11.3, 0.8), 2)
    else:
        p["FACE_AMOUNT"] = rng.choice([1000000, 2000000, 5000000]) if org else rng.choice([100000, 250000, 500000, 750000, 1000000])
        p["MODAL_PREMIUM"] = round(p["FACE_AMOUNT"] * rng.uniform(0.0006, 0.0025) / (12 if not org else 1), 2)
        p["PREMIUM_MODE"] = "Annual" if org else rng.choice(["Monthly", "Monthly", "Annual"])
    if status == "Rescinded":
        p["TERMINATION_REASON"] = "Material Misrepresentation"
    if r.get("CATEGORY"):
        p["CUSTOMER_TIER"] = r["CATEGORY"]
    return p


def strip(r, drop):
    return {k: v for k, v in r.items() if k not in drop}


files = {
    "pc_policy_admin_extract.jsonl": [],
    "life_annuity_admin_extract.jsonl": [],
    "credit_header.jsonl": [],
    "business_registry.jsonl": [],
    "siu_cases.jsonl": [],
    "sanctions_pep.jsonl": [],
}
newds = {}  # (old ds, record id) -> new ds

for r in customers:
    ds = feed[r["RECORD_ID"]]
    base = strip(r, ("DATA_SOURCE", "RECORD_ID", "AMOUNT", "DATE", "STATUS", "CATEGORY"))
    pay = pc_policy(r) if ds == PC else life_contract(r)
    files["pc_policy_admin_extract.jsonl" if ds == PC else "life_annuity_admin_extract.jsonl"].append(
        {"DATA_SOURCE": ds, "RECORD_ID": r["RECORD_ID"], **base, **pay})
    newds[("CUSTOMERS", r["RECORD_ID"])] = ds

for r in reference:
    bureau = r.get("CATEGORY") == "Credit Header"
    ds = "CREDIT_BUREAU" if bureau else "BUSINESS_REGISTRY"
    n = {"DATA_SOURCE": ds, "RECORD_ID": r["RECORD_ID"], **strip(r, ("DATA_SOURCE", "RECORD_ID", "STATUS", "CATEGORY", "DATE"))}
    if bureau:
        n["FILE_STATUS"] = r.get("STATUS")
    else:
        n["REGISTRY_ROLE"] = r.get("CATEGORY")
        n["REGISTRY_STATUS"] = r.get("STATUS")
        if r.get("DATE"):
            n["INCORPORATED"] = r["DATE"]
    files["credit_header.jsonl" if bureau else "business_registry.jsonl"].append(n)
    newds[("REFERENCE", r["RECORD_ID"])] = ds

def list_date(d):
    for fmt in ("%m/%d/%y", "%m/%d/%Y", "%Y"):
        try:
            return datetime.strptime(d, fmt).date().isoformat()
        except (TypeError, ValueError):
            pass
    return d


FRAUD_TYPES = ["Staged Accident", "Inflated Claim", "Application Misrepresentation", "Premium Fraud", "Provider Billing Fraud"]
ROLES = ["Claimant", "Insured", "Claimant", "Witness", "Insured", "Medical Provider", "Claimant", "Attorney"]
LOBS = {"Staged Accident": "Personal Auto", "Inflated Claim": "Homeowners", "Application Misrepresentation": "Personal Auto",
        "Premium Fraud": "Workers Compensation", "Provider Billing Fraud": "Personal Auto"}
fraud = sorted([r for r in watchlist if r.get("CATEGORY") == "Fraud"], key=lambda r: r["RECORD_ID"])
for i, r in enumerate(fraud):
    ft = FRAUD_TYPES[i % len(FRAUD_TYPES)]
    st = r.get("STATUS")
    disp = "Unsubstantiated" if st == "Inactive" else "Open" if st == "Current" or i % 4 == 3 else \
        ("Referred to Law Enforcement / DOI" if i % 5 == 1 else "Substantiated")
    ref = list_date(r.get("DATE"))
    files["siu_cases.jsonl"].append(
        {"DATA_SOURCE": "SIU_CASES", "RECORD_ID": r["RECORD_ID"], **strip(r, ("DATA_SOURCE", "RECORD_ID", "STATUS", "CATEGORY", "DATE")),
         "SIU_CASE_NUMBER": f"SIU-{str(ref)[:4]}-{10000 + h(r['RECORD_ID']) * 97 + i:05d}", "REFERRAL_DATE": ref, "FRAUD_TYPE": ft,
         "ROLE_IN_CASE": ROLES[i % len(ROLES)], "LINE_OF_BUSINESS": LOBS[ft], "CASE_DISPOSITION": disp})
    newds[("WATCHLIST", r["RECORD_ID"])] = "SIU_CASES"

PEP_POS = [("State Senator", "US"), ("Federal Judge", "US"), ("Deputy Minister of Finance", "GB")]
npep = 0
for r in sorted([r for r in watchlist if r.get("CATEGORY") != "Fraud"], key=lambda r: r["RECORD_ID"]):
    sanc = r.get("CATEGORY") == "Sanctioned"
    n = {"DATA_SOURCE": "SANCTIONS_PEP", "RECORD_ID": r["RECORD_ID"], **strip(r, ("DATA_SOURCE", "RECORD_ID", "STATUS", "CATEGORY", "DATE")),
         "LIST_TYPE": "Sanctions" if sanc else "PEP", "LIST_NAME": "OFAC SDN" if sanc else "PEP database", "LISTED_DATE": list_date(r.get("DATE"))}
    if sanc:
        cyr = any(any("\u0400" <= ch <= "\u04ff" for ch in str(v)) for f in r.get("FEATURES", []) for v in f.values())
        n["SANCTIONS_PROGRAM"] = "BELARUS" if cyr else "SDGT"
    else:
        n["PEP_POSITION"], n["PEP_COUNTRY"] = PEP_POS[npep % len(PEP_POS)]
        npep += 1
    files["sanctions_pep.jsonl"].append(n)
    newds[("WATCHLIST", r["RECORD_ID"])] = "SANCTIONS_PEP"

for name, rows in files.items():
    with open(f"{out}/{name}", "w") as fh:
        for row in rows:
            fh.write(json.dumps(row) + "\n")
    print(f"{name:34s} {len(rows):4d}")

# private keys, same columns/order as their sources; records not loaded (CUSTOMER_EVENTS) dropped
for srcname, outname in (("actual_truthset_key.csv", "insurance_truthset_key.csv"),
                         ("alternate_truthset_key.csv", "insurance_alternate_key.csv")):
    rows = list(csv.DictReader(open(f"{src}/{srcname}")))
    cols = list(rows[0].keys())
    with open(f"{out}/{outname}", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        for row in rows:
            k = (row["DATA_SOURCE"], row["RECORD_ID"])
            if k in newds:
                row["DATA_SOURCE"] = newds[k]
                w.writerow(row)

both = sum(1 for recs in clusters.values() if len({feed[r["RECORD_ID"]] for r in recs}) == 2)
orgs = defaultdict(int)
for r in customers:
    if rtype(r) == "ORGANIZATION":
        orgs[feed[r["RECORD_ID"]]] += 1
print("customer clusters:", len(clusters), " spanning both systems:", both, " org records by feed:", dict(orgs))
