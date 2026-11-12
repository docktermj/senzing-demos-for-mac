#! /usr/bin/env python3
"""Data collection, Step 2: generate the three synthetic Customer 360 source files.

Everything here is invented. Writes:
  data/raw/crm_system.csv, data/raw/billing_system.csv, data/raw/support_tickets.csv
  data/ground_truth/scenario_ground_truth.csv   (which records are the same invented person)
  data/temp/generation_report.json              (measured counts and scores; read by the registry step)

Design (see docs/business_problem.md and config/data_sources.yaml):
  * 6,000 distinct invented people spread over the three systems with real cross-source overlap.
  * Shape differences: CRM one joined name + free-text address; Billing first/last + parsed address;
    Support sparse (name and email only) with typos and inconsistent casing.
  * Quality gaps: Billing is meant to land in the 70-79 band, CRM and Support at >=80.
  * Every email and phone belongs to exactly one invented person (no unlisted shares).
  * Names: 20 deliberate same-name pairs (hard negatives); all other names are unique.
Usage: python3 src/collect/generate_scenario_data.py [seed] [--people-only]
"""

import csv
import datetime
import json
import random
import re
import sys
from collections import Counter
from pathlib import Path

_numeric = [a for a in sys.argv[1:] if a.isdigit()]
SEED = int(_numeric[0]) if _numeric else 20261109
PEOPLE_ONLY = "--people-only" in sys.argv  # write data/temp/people.json and stop; no source files
rng = random.Random(SEED)

RAW = Path("data/raw")
TRUTH = Path("data/ground_truth")
TEMP = Path("data/temp")
for d in (RAW, TRUTH, TEMP):
    d.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------- name pools
FIRST = """Aaron Abigail Adam Adrian Aiden Alan Albert Alex Alexis Alice Alicia Allison Amanda Amber Amy Andrea Andrew
Angela Anita Ann Anna Anthony Antonio April Arthur Ashley Austin Barbara Barry Beatrice Ben Benjamin Beth Betty Beverly
Bill Blake Bonnie Brad Brandon Brenda Brian Bridget Brittany Bruce Bryan Caleb Calvin Cameron Carl Carla Carlos Carmen
Carol Caroline Carrie Casey Catherine Cecilia Chad Charles Charlotte Chelsea Cheryl Chloe Chris Christian Christine
Cindy Claire Clara Clarence Claudia Cody Colin Connor Corey Courtney Craig Crystal Curtis Cynthia Dale Dan Daniel
Danielle Darren Dave David Dawn Dean Deborah Debra Denise Dennis Derek Diana Diane Dominic Donald Donna Doris Dorothy
Douglas Drew Dustin Dylan Earl Edgar Edith Edward Eileen Elaine Eleanor Elena Eli Elijah Elizabeth Ella Ellen Emily
Emma Eric Erica Erik Erin Ethan Eugene Eva Evan Evelyn Faith Felix Fiona Florence Frances Francis Frank Fred Gabriel
Gail Gary Gavin Gene George Gerald Gina Gladys Glen Gloria Grace Graham Greg Gregory Gwen Hailey Hannah Harold Harry
Harvey Hazel Heather Heidi Helen Henry Holly Howard Hugh Ian Irene Isaac Isabel Ivan Jack Jackie Jacob Jade Jake James
Jamie Jane Janet Janice Jared Jason Jasmine Jean Jeff Jenna Jennifer Jeremy Jerry Jesse Jessica Jill Jim Joan Joanna
Jodi Joe Joel John Jonathan Jordan Joseph Josh Joyce Juan Judith Judy Julia Julian Julie June Justin Karen Katherine
Kathleen Kathy Katie Kayla Keith Kelly Ken Kenneth Kevin Kim Kimberly Kirk Kyle Lana Larry Laura Lauren Lawrence Leah
Lee Leo Leon Leslie Lewis Lila Lillian Linda Lindsey Lisa Logan Lois Lori Louis Lucas Lucy Luke Lydia Lynn Madeline
Marcus Margaret Maria Marie Marilyn Mark Marsha Martha Martin Mary Mason Matthew Maureen Maya Megan Melanie Melissa
Michael Michelle Miguel Mike Mildred Miranda Mitchell Molly Monica Morgan Nancy Naomi Natalie Nathan Neil Nicholas
Nicole Nina Noah Nora Norman Olivia Oscar Owen Paige Pam Pamela Patricia Patrick Paul Paula Peggy Penny Peter Philip
Phyllis Priscilla Rachel Ralph Randy Raymond Rebecca Regina Renee Rhonda Richard Rick Rita Robert Robin Rodney Roger
Ronald Rosa Rose Ross Roy Ruby Russell Ruth Ryan Sally Samantha Samuel Sandra Sara Sarah Scott Sean Sharon Shawn
Sheila Shelley Sherry Shirley Simon Sophia Stacy Stanley Stephanie Stephen Steve Steven Stuart Sue Susan Suzanne
Sylvia Tammy Tanya Tara Taylor Ted Teresa Terry Theresa Thomas Tim Timothy Tina Todd Tom Tony Tracy Travis Trevor
Tyler Valerie Vanessa Vernon Veronica Victor Victoria Vincent Violet Virginia Wade Walter Wanda Warren Wayne Wendy
William Willie Yvonne Zachary Zoe""".split()

STEMS = """Ash Black Bram Brook Clay Cole Crest Dale Dun Elm Fair Fern Field Flint Fox Glen Gray Green Hale Hart Hawk
Hazel High Hill Holt Hunt Kirk Lake Lane Lark Lind Long Marsh Mead Mill Moor North Oak Park Pine Rain Reed Ridge River
Rock Rose Rush Sand Shaw Silver Snow Stone Storm Swan Thorn Tide Vale Wald Ward Water West White Wild Wood Wren York
Alder Barlow Castle Dray Eden Frost Gold Harlow Iron Jasper Kest Lang Mor Nash Orr Pell Quill Rav Sut Tay Ul Vance
Wex Yar Zel""".split()
SUFFIXES = """ford wood field ton ley man worth well wick by dale mont ham ner son ridge stead gate brook land shaw berry
croft hurst more den vale combe low""".split()

NICKNAMES = {
    "Robert": "Bob", "William": "Bill", "Richard": "Rick", "James": "Jim", "Michael": "Mike", "Thomas": "Tom",
    "Elizabeth": "Liz", "Katherine": "Kate", "Jennifer": "Jen", "Margaret": "Peggy", "Christopher": "Chris",
    "Jonathan": "Jon", "Daniel": "Dan", "Matthew": "Matt", "Patricia": "Pat", "Samuel": "Sam", "Joseph": "Joe",
    "Benjamin": "Ben", "Alexander": "Alex", "Nicholas": "Nick", "Timothy": "Tim", "Steven": "Steve",
    "Kenneth": "Ken", "Edward": "Ted", "Susan": "Sue", "Deborah": "Debbie", "Kimberly": "Kim", "Pamela": "Pam",
}

STREETS = """Maple Cedar Elm Oak Pine Birch Willow Aspen Walnut Chestnut Hickory Sycamore Poplar Spruce Magnolia Juniper
Laurel Hawthorne Dogwood Sunset Sunrise Lakeview Hillcrest Ridgeway Meadow Orchard Prairie Valley Summit Harbor Mill
Church School Market Bridge Station Park Garden River Canyon Cypress Highland Cherry Franklin Jefferson Lincoln
Madison Monroe Jackson Adams Wilson Grant Hamilton Washington Liberty Union Central Spring Forest Brookside Windsor
Kingston Easton Fairview Oakwood Pinecrest Rosewood Stonebridge Westgate Eastwood Northfield Southgate Briarwood
Heritage Colonial Pioneer Cardinal Heron Falcon Sparrow Bluebird Meadowlark""".split()
STREET_TYPES = [("Street", "St"), ("Avenue", "Ave"), ("Road", "Rd"), ("Drive", "Dr"), ("Lane", "Ln"),
                ("Court", "Ct"), ("Boulevard", "Blvd"), ("Terrace", "Ter"), ("Way", "Way"), ("Place", "Pl")]

CITIES = [
    ("Springfield", "OR", ["97477", "97478", "97475"]), ("Portland", "OR", ["97201", "97214", "97232"]),
    ("Eugene", "OR", ["97401", "97402", "97405"]), ("Salem", "OR", ["97301", "97302", "97306"]),
    ("Tacoma", "WA", ["98402", "98405", "98418"]), ("Spokane", "WA", ["99201", "99205", "99208"]),
    ("Boise", "ID", ["83702", "83705", "83709"]), ("Reno", "NV", ["89501", "89502", "89509"]),
    ("Sacramento", "CA", ["95814", "95816", "95822"]), ("Fresno", "CA", ["93701", "93704", "93720"]),
    ("Denver", "CO", ["80202", "80205", "80210"]), ("Boulder", "CO", ["80301", "80302", "80304"]),
    ("Tucson", "AZ", ["85701", "85705", "85710"]), ("Mesa", "AZ", ["85201", "85203", "85210"]),
    ("Austin", "TX", ["78701", "78704", "78745"]), ("Plano", "TX", ["75023", "75024", "75074"]),
    ("Omaha", "NE", ["68102", "68105", "68114"]), ("Madison", "WI", ["53703", "53704", "53711"]),
    ("Columbus", "OH", ["43201", "43205", "43215"]), ("Dayton", "OH", ["45402", "45405", "45410"]),
    ("Raleigh", "NC", ["27601", "27603", "27609"]), ("Durham", "NC", ["27701", "27703", "27705"]),
    ("Richmond", "VA", ["23219", "23220", "23225"]), ("Albany", "NY", ["12203", "12206", "12208"]),
    ("Buffalo", "NY", ["14201", "14207", "14222"]), ("Hartford", "CT", ["06103", "06105", "06114"]),
    ("Providence", "RI", ["02903", "02906", "02908"]), ("Burlington", "VT", ["05401", "05408", "05401"]),
    ("Nashville", "TN", ["37203", "37206", "37211"]), ("Memphis", "TN", ["38103", "38104", "38111"]),
    ("Atlanta", "GA", ["30303", "30306", "30312"]), ("Savannah", "GA", ["31401", "31405", "31406"]),
    ("Tampa", "FL", ["33602", "33606", "33609"]), ("Orlando", "FL", ["32801", "32803", "32806"]),
    ("Louisville", "KY", ["40202", "40204", "40208"]), ("Lexington", "KY", ["40502", "40503", "40508"]),
    ("Minneapolis", "MN", ["55401", "55404", "55408"]), ("Duluth", "MN", ["55802", "55805", "55812"]),
    ("Des Moines", "IA", ["50309", "50310", "50314"]), ("Wichita", "KS", ["67202", "67203", "67211"]),
    ("Tulsa", "OK", ["74103", "74104", "74112"]), ("Little Rock", "AR", ["72201", "72205", "72207"]),
    ("Baton Rouge", "LA", ["70802", "70806", "70808"]), ("Jackson", "MS", ["39201", "39202", "39211"]),
    ("Mobile", "AL", ["36602", "36604", "36608"]), ("Charleston", "SC", ["29401", "29403", "29407"]),
    ("Pittsburgh", "PA", ["15201", "15206", "15217"]), ("Allentown", "PA", ["18101", "18102", "18104"]),
    ("Newark", "NJ", ["07102", "07104", "07105"]), ("Wilmington", "DE", ["19801", "19802", "19805"]),
    ("Portland", "ME", ["04101", "04102", "04103"]), ("Manchester", "NH", ["03101", "03102", "03104"]),
]
DOMAINS = ["fmail.com", "hmail.net", "outbox.org", "inbox.example", "mailhub.net", "postbox.io",
           "netpost.com", "webmail.example", "quickmail.org", "zmail.com", "sendly.net", "mymail.example"]
SUBJECTS = ["Cannot log in", "Billing question", "Update my address", "Cancel subscription", "Password reset",
            "Invoice not received", "Change plan", "Service outage", "Refund request", "Update contact details",
            "Duplicate charge", "Account locked", "Question about my order", "Feature request", "Slow performance"]
PLANS = ["basic", "standard", "premium", "family", "business"]
CAMPAIGNS = ["spring-promo", "web-signup", "newsletter", "referral", "trade-show", "partner-offer", "winter-sale"]

# -------------------------------------------------------- group sizes (people)
N_ALL3, N_CRM_BIL, N_CRM_SUP, N_BIL_SUP = 600, 1500, 300, 200
N_CRM_ONLY, N_BIL_ONLY, N_SUP_ONLY = 1300, 1600, 500
CRM_DUPS, BIL_DUPS, SUP_REPEATS = 300, 100, 400
HARD_NEGATIVE_PAIRS = 20


def norm_name(first, last):
    return re.sub(r"[^a-z0-9]", "", (first + last).lower())


# ----------------------------------------------------------------- the people
total_people = N_ALL3 + N_CRM_BIL + N_CRM_SUP + N_BIL_SUP + N_CRM_ONLY + N_BIL_ONLY + N_SUP_ONLY
surnames = sorted({s + x for s in STEMS for x in SUFFIXES})
used_names, people = set(), []
area_codes = []
while len(area_codes) < 90:
    a = f"{rng.randint(2, 9)}{rng.randint(0, 8)}{rng.randint(0, 9)}"
    if a[1:] != "11" and a not in area_codes:
        area_codes.append(a)
used_phones, used_streets = set(), set()


def fresh_phone():
    while True:
        # 555-01XX is the reserved fictional range: area + 555 + 01 + two digits
        p = f"{rng.choice(area_codes)}55501{rng.randint(0, 99):02d}"
        if p not in used_phones:
            used_phones.add(p)
            return p


def fresh_street():
    while True:
        long, short = rng.choice(STREET_TYPES)
        number = rng.randint(100, 9899)
        name = rng.choice(STREETS)
        key = (number, name)
        if key not in used_streets:
            used_streets.add(key)
            return number, name, long, short


for eid in range(1, total_people + 1):
    while True:
        first, last = rng.choice(FIRST), rng.choice(surnames)
        if norm_name(first, last) not in used_names:
            used_names.add(norm_name(first, last))
            break
    number, sname, long, short = fresh_street()
    city, state, zips = rng.choice(CITIES)
    people.append({
        "eid": eid, "first": first, "last": last,
        "number": number, "sname": sname, "stype_long": long, "stype_short": short,
        "city": city, "state": state, "zip": rng.choice(zips),
        "phone": fresh_phone(),
        "email": f"{first.lower()}.{last.lower()}{eid}@{rng.choice(DOMAINS)}",
    })

# group assignment (people ids are shuffled so groups are not aligned with name or id order)
order = people[:]
rng.shuffle(order)
cursor = 0


def take(n):
    global cursor
    chunk = order[cursor:cursor + n]
    cursor += n
    return chunk


g_all3, g_crm_bil, g_crm_sup, g_bil_sup = take(N_ALL3), take(N_CRM_BIL), take(N_CRM_SUP), take(N_BIL_SUP)
g_crm_only, g_bil_only, g_sup_only = take(N_CRM_ONLY), take(N_BIL_ONLY), take(N_SUP_ONLY)
assert cursor == total_people

# deliberate same-name pairs (hard negatives): different people, different everything else
pair_ids = []
for i in range(HARD_NEGATIVE_PAIRS):
    a, b = g_crm_only[i], g_bil_only[i]
    b["first"], b["last"] = a["first"], a["last"]
    b["email"] = f"{b['first'].lower()}.{b['last'].lower()}{b['eid']}@{rng.choice(DOMAINS)}"
    pair_ids.append((a["eid"], b["eid"]))

if PEOPLE_ONLY:
    (TEMP / "people.json").write_text(json.dumps(people), encoding="utf-8")
    print(f"wrote {TEMP / 'people.json'} ({len(people)} people); no source files written")
    sys.exit(0)

crm_people = g_all3 + g_crm_bil + g_crm_sup + g_crm_only
bil_people = g_all3 + g_crm_bil + g_bil_sup + g_bil_only
sup_people = g_all3 + g_crm_sup + g_bil_sup + g_sup_only

# ------------------------------------------------------------------ formatters


def fmt_phone(digits, pattern):
    a, b, c = digits[:3], digits[3:6], digits[6:]
    return {
        "dash": f"{a}-{b}-{c}", "paren": f"({a}) {b}-{c}", "dot": f"{a}.{b}.{c}",
        "plain": digits, "intl": f"+1 {a} {b} {c}",
    }[pattern]


def pick(weights):
    r, acc = rng.random(), 0.0
    for key, w in weights:
        acc += w
        if r < acc:
            return key
    return weights[-1][0]


def rand_date(start=2018, end=2024):
    d0 = datetime.date(start, 1, 1)
    return d0 + datetime.timedelta(days=rng.randint(0, (datetime.date(end, 12, 31) - d0).days))


def fmt_date(d, us_rate):
    return d.strftime("%m/%d/%Y") if rng.random() < us_rate else d.isoformat()


def typo(s):
    if len(s) < 4:
        return s
    i = rng.randint(1, len(s) - 2)
    kind = rng.choice(["swap", "drop", "dup"])
    if kind == "swap":
        return s[:i] + s[i + 1] + s[i] + s[i + 2:]
    if kind == "drop":
        return s[:i] + s[i + 1:]
    return s[:i] + s[i] + s[i:]


def street_text(p, abbreviate_rate):
    kind = p["stype_short"] if rng.random() < abbreviate_rate else p["stype_long"]
    return f"{p['number']} {p['sname']} {kind}"


# ------------------------------------------------------------- source builders
def crm_row(p, rid, campaign, vary=False):
    name = f"{p['first']} {p['last']}"
    if rng.random() < 0.06 or vary and rng.random() < 0.3:
        name = f"{p['first']} {rng.choice('ABCDEFGHJKLMNPRSTW')}. {p['last']}"
    address = f"{street_text(p, 0.10)}, {p['city']}, {p['state']} {p['zip']}"
    phone = fmt_phone(p["phone"], pick([("dash", 0.84), ("paren", 0.08), ("dot", 0.05), ("intl", 0.03)]))
    return {
        "crm_id": rid, "full_name": name,
        "address": "" if rng.random() < 0.03 else address,
        "phone": "" if rng.random() < 0.12 else phone,
        "email": "" if rng.random() < (0.30 if vary else 0.10) else p["email"],
        "created_date": fmt_date(rand_date(), 0.08), "campaign": campaign,
    }


def billing_row(p, rid):
    first = NICKNAMES.get(p["first"], p["first"]) if rng.random() < 0.04 else p["first"]
    no_address = rng.random() < 0.40
    row = {
        "billing_id": rid,
        "first_name": "" if rng.random() < 0.08 else first,
        "last_name": "" if rng.random() < 0.04 else p["last"],
        "street": "" if no_address or rng.random() < 0.10 else street_text(p, 0.20),
        "city": "" if no_address or rng.random() < 0.10 else p["city"],
        "state": "" if no_address or rng.random() < 0.10 else (p["state"].lower() if rng.random() < 0.04 else p["state"]),
        "zip": "" if no_address or rng.random() < 0.10 else (
            p["zip"] + f"-{rng.randint(1000, 9999)}" if rng.random() < 0.06 else p["zip"]),
        "phone": "" if rng.random() < 0.62 else fmt_phone(
            p["phone"], pick([("dash", 0.58), ("paren", 0.22), ("plain", 0.12), ("dot", 0.08)])),
        "plan": rng.choice(PLANS),
        "last_invoice_date": fmt_date(rand_date(2023, 2024), 0.06),
    }
    if not any(row[k] for k in ("first_name", "last_name", "street", "phone")):
        row["last_name"] = p["last"]  # never emit an anonymous record
    return row


def support_row(p, rid):
    name = f"{p['first']} {p['last']}"
    if rng.random() < 0.05:
        name = f"{p['first']} {typo(p['last'])}"
    case = pick([("title", 0.82), ("upper", 0.10), ("lower", 0.08)])
    name = name.upper() if case == "upper" else name.lower() if case == "lower" else name
    email = p["email"]
    if rng.random() < 0.04:
        local, domain = email.split("@")
        email = f"{typo(local)}@{domain}"
    row = {
        "ticket_id": rid, "name": "" if rng.random() < 0.02 else name,
        "email": "" if rng.random() < 0.10 else email,
        "subject": rng.choice(SUBJECTS), "opened_date": fmt_date(rand_date(2022, 2024), 0.03),
    }
    if not row["name"] and not row["email"]:
        row["email"] = p["email"]
    return row


# ------------------------------------------------------------------- generate
truth = []  # (source, record_id, person_id)
crm_rows, bil_rows, sup_rows = [], [], []

for i, p in enumerate(crm_people):
    rid = f"CRM-{len(crm_rows) + 1:06d}"
    crm_rows.append((crm_row(p, rid, rng.choice(CAMPAIGNS)), p["eid"]))
for p in rng.sample(crm_people, CRM_DUPS):  # same person entered again through another campaign
    rid = f"CRM-{len(crm_rows) + 1:06d}"
    crm_rows.append((crm_row(p, rid, rng.choice(CAMPAIGNS), vary=True), p["eid"]))

for p in bil_people:
    rid = f"BIL-{len(bil_rows) + 1:06d}"
    bil_rows.append((billing_row(p, rid), p["eid"]))
for p in rng.sample(bil_people, BIL_DUPS):  # second billing account for the same customer
    rid = f"BIL-{len(bil_rows) + 1:06d}"
    bil_rows.append((billing_row(p, rid), p["eid"]))

for p in sup_people:
    rid = f"TKT-{len(sup_rows) + 1:06d}"
    sup_rows.append((support_row(p, rid), p["eid"]))
for p in rng.sample(sup_people, SUP_REPEATS):  # a repeat ticket from the same person
    rid = f"TKT-{len(sup_rows) + 1:06d}"
    sup_rows.append((support_row(p, rid), p["eid"]))

for rows in (crm_rows, bil_rows, sup_rows):
    rng.shuffle(rows)


def write_csv(path, rows, key_col, source):
    cols = list(rows[0][0].keys())
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        for row, eid in rows:
            w.writerow(row)
            truth.append((source, row[key_col], eid))
    return cols


crm_cols = write_csv(RAW / "crm_system.csv", crm_rows, "crm_id", "CRM_SYSTEM")
bil_cols = write_csv(RAW / "billing_system.csv", bil_rows, "billing_id", "BILLING_SYSTEM")
sup_cols = write_csv(RAW / "support_tickets.csv", sup_rows, "ticket_id", "SUPPORT_TICKETS")
with (TRUTH / "scenario_ground_truth.csv").open("w", newline="", encoding="utf-8") as fh:
    w = csv.writer(fh)
    w.writerow(["data_source", "record_id", "person_id"])
    w.writerows(truth)


# ----------------------------------------------------------------- self-check
def present(v):
    return v is not None and str(v).strip() != ""


def shape(v):
    return re.sub(r"[A-Za-z]", "a", re.sub(r"\d", "9", v))


def case_of(v):
    return "upper" if v.isupper() else "lower" if v.islower() else "title"


def measure(rows, key_col, applicable, pattern_fields):
    """Module 5 Step 6: completeness over applicable fields (key included), format_consistency, duplicate_rate."""
    n = len(rows)
    per_record = [
        (1 + sum(present(r[f]) for f in applicable)) / (1 + len(applicable)) for r, _ in rows
    ]
    completeness = 100 * sum(per_record) / n
    match = pop = 0
    per_field = {}
    for f, fn in pattern_fields.items():
        vals = [fn(r[f]) for r, _ in rows if present(r[f])]
        if not vals:
            continue
        dom, cnt = Counter(vals).most_common(1)[0]
        per_field[f] = round(100 * cnt / len(vals), 1)
        match += cnt
        pop += len(vals)
    fc = 100 * match / pop
    ids = Counter(r[key_col] for r, _ in rows)
    dup = 100 * sum(c for c in ids.values() if c > 1) / n
    score = 0.70 * completeness + 0.25 * fc + 0.05 * (100 - dup)
    absent_slots = 100 - completeness
    return {
        "records": n, "completeness": round(completeness, 1), "empty_slot_share": round(absent_slots, 1),
        "format_consistency": round(fc, 1), "format_by_field": per_field,
        "duplicate_rate": round(dup, 1), "score": round(score, 1),
    }


def band(s):
    return ">=80" if s >= 80 else "70-79" if s >= 70 else "<70"


results = {
    "CRM_SYSTEM": measure(crm_rows, "crm_id", ["full_name", "address", "phone", "email"],
                          {"phone": shape, "created_date": shape}),
    "BILLING_SYSTEM": measure(bil_rows, "billing_id",
                              ["first_name", "last_name", "street", "city", "state", "zip", "phone"],
                              {"phone": shape, "zip": shape, "state": case_of, "last_invoice_date": shape}),
    "SUPPORT_TICKETS": measure(sup_rows, "ticket_id", ["name", "email"],
                               {"name": case_of, "opened_date": shape}),
}
for r in results.values():
    r["band"] = band(r["score"])

# identifier collisions: distinct people sharing an email or a phone value (none are declared)
person_phone, person_email = {}, {}
for p in people:
    person_phone.setdefault(p["phone"], set()).add(p["eid"])
    person_email.setdefault(p["email"], set()).add(p["eid"])
collisions = sum(len(v) > 1 for v in person_phone.values()) + sum(len(v) > 1 for v in person_email.values())

# name sharing across distinct people
by_name = {}
for p in people:
    by_name.setdefault(norm_name(p["first"], p["last"]), set()).add(p["eid"])
sharing_entities = sum(len(v) for v in by_name.values() if len(v) > 1)
sharing_pairs = sum(len(v) * (len(v) - 1) // 2 for v in by_name.values() if len(v) > 1)

report = {
    "seed": SEED, "people": len(people), "files": {
        "CRM_SYSTEM": {"path": "data/raw/crm_system.csv", "columns": crm_cols},
        "BILLING_SYSTEM": {"path": "data/raw/billing_system.csv", "columns": bil_cols},
        "SUPPORT_TICKETS": {"path": "data/raw/support_tickets.csv", "columns": sup_cols},
    },
    "quality": results,
    "identifier_collisions": collisions,
    "name_collisions": {"declared_pairs": HARD_NEGATIVE_PAIRS, "measured_pairs": sharing_pairs,
                        "measured_entities": sharing_entities,
                        "measured_share": round(sharing_entities / len(people), 4)},
    "cross_source": {
        "in_all_three": N_ALL3, "crm_and_billing": N_ALL3 + N_CRM_BIL, "crm_and_support": N_ALL3 + N_CRM_SUP,
        "billing_and_support": N_ALL3 + N_BIL_SUP,
        "people_in_2plus_sources": N_ALL3 + N_CRM_BIL + N_CRM_SUP + N_BIL_SUP,
    },
}
(TEMP / "generation_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps({k: report[k] for k in ("seed", "people", "quality", "identifier_collisions",
                                         "name_collisions", "cross_source")}, indent=2))
