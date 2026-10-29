import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from statistics import mean
from urllib.parse import unquote_plus

BENIN = timezone(timedelta(hours=1))
SLOW_THRESHOLD = timedelta(seconds=5)
MAX_AGE = 35

EXAMS = {
    "DOUANES": {"exam_date": date(2026, 12, 12), "closing": datetime(2026, 11, 30, 22, 59, tzinfo=timezone.utc)},
    "IMPOTS": {"exam_date": date(2026, 12, 12), "closing": datetime(2026, 11, 30, 22, 59, tzinfo=timezone.utc)},
    "TRESOR": {"exam_date": date(2026, 12, 19), "closing": datetime(2026, 11, 20, 22, 59, tzinfo=timezone.utc)},
}

REQUEST_LINE = re.compile(
    r"^(?P<ts>\S+) (?P<level>\w+)\s+\[req:(?P<req>\w+)\] (?P<ip>\S+) (?P<method>[A-Z]+) (?P<path>\S+)"
    r" -> (?P<status>\d{3}) \((?P<ms>\d+) ms\)(?P<rest>.*)$"
)
IDENTITY_LINE = re.compile(
    r"^(?P<ts>\S+) (?P<level>\w+)\s+\[req:(?P<req>\w+)\] appel identite GET /identite/(?P<npi>\S+)"
    r" -> (?P<status>\d{3}) \((?P<ms>\d+) ms\)"
)
FIELD = re.compile(r'(\w+)=("[^"]*"|\S+)')
CENTER_LIST = re.compile(r"^/api/centres/(?P<center>\w+)/inscrits")
SUSPICIOUS_SEARCH = re.compile(r"'|--|;|\bunion\b|\bselect\b|\bor\b", re.IGNORECASE)


@dataclass
class Event:
    end: datetime
    duration: timedelta
    req: str
    status: int
    ip: str = ""
    method: str = ""
    path: str = ""
    fields: dict = field(default_factory=dict)

    @property
    def start(self):
        return self.end - self.duration


def parse_instant(text):
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def benin(instant):
    return instant.astimezone(BENIN).strftime("%d/%m/%Y %H:%M:%S")


def read_log(path):
    requests, identity_calls, skipped = [], [], 0
    with open(path, encoding="utf-8", errors="replace") as log:
        for line in log:
            line = line.rstrip("\n")
            if m := IDENTITY_LINE.match(line):
                identity_calls.append(Event(
                    end=parse_instant(m["ts"]), duration=timedelta(milliseconds=int(m["ms"])),
                    req=m["req"], status=int(m["status"]), fields={"npi": m["npi"]},
                ))
            elif m := REQUEST_LINE.match(line):
                fields = {key: value.strip('"') for key, value in FIELD.findall(m["rest"])}
                requests.append(Event(
                    end=parse_instant(m["ts"]), duration=timedelta(milliseconds=int(m["ms"])),
                    req=m["req"], status=int(m["status"]), ip=m["ip"],
                    method=m["method"], path=m["path"], fields=fields,
                ))
            elif line.strip():
                skipped += 1
    return requests, identity_calls, skipped


def successful_registrations(requests):
    return [r for r in requests if r.method == "POST" and r.path == "/api/inscriptions" and r.status == 201]


def rejections(requests, reason):
    return [
        r for r in requests
        if r.method == "POST" and r.path == "/api/inscriptions" and reason in r.fields.get("erreur", "")
    ]


def age_on_december_31(birth_date, year):
    return year - birth_date.year


def duplicate_table_numbers(requests):
    by_number = defaultdict(list)
    for r in successful_registrations(requests):
        by_number[r.fields["numero"]].append(r)
    return {number: items for number, items in sorted(by_number.items()) if len(items) > 1}


def duplicate_registrations(requests):
    by_candidate = defaultdict(list)
    for r in successful_registrations(requests):
        by_candidate[(r.fields["npi"], r.fields["concours"])].append(r)
    return {key: items for key, items in sorted(by_candidate.items()) if len(items) > 1}


def slow_identity_period(requests, identity_calls):
    slow = [c for c in identity_calls if c.duration >= SLOW_THRESHOLD]
    if not slow:
        return None
    affected = {c.req for c in slow}
    by_req = {r.req: r for r in requests}
    by_path = defaultdict(int)
    for req in affected:
        if req in by_req:
            by_path[re.sub(r"/\d+/", "/{id}/", by_req[req].path)] += 1
    return {
        "start": min(c.start for c in slow),
        "end": max(c.end for c in slow),
        "requests": len(affected),
        "by_path": dict(by_path),
        "max_duration": max(c.duration for c in slow),
    }


def wrongful_closing_rejections(requests):
    return [
        r for r in rejections(requests, "Inscriptions closes")
        if r.start <= EXAMS[r.fields["concours"]]["closing"]
    ]


def wrongful_age_rejections(requests):
    result = []
    for r in rejections(requests, "Âge limite"):
        birth_date = date.fromisoformat(r.fields["naissance"])
        year = EXAMS[r.fields["concours"]]["exam_date"].year
        if age_on_december_31(birth_date, year) <= MAX_AGE:
            result.append(r)
    return result


def center_list_durations(requests):
    by_center = defaultdict(list)
    for r in requests:
        if r.method == "GET" and (m := CENTER_LIST.match(r.path)):
            by_center[m["center"]].append(r)
    return {center: items for center, items in sorted(by_center.items())}


def suspicious_searches(requests):
    return [
        r for r in requests
        if r.path.startswith("/api/inscrits/recherche") and SUSPICIOUS_SEARCH.search(unquote_plus(r.path))
    ]


def ms(duration):
    return f"{duration / timedelta(milliseconds=1):.0f} ms"


def heading(text):
    print(f"\n=== {text} ===")


def report(path):
    requests, identity_calls, skipped = read_log(path)
    print(f"Journal : {path}")
    print(f"Requêtes : {len(requests)}, appels identité : {len(identity_calls)}, lignes non reconnues : {skipped}")
    print("Toutes les heures sont en heure du Bénin (UTC+1).")

    duplicates = duplicate_table_numbers(requests)
    heading(f"1. Numéros de table attribués plusieurs fois : {len(duplicates)}")
    for number, items in duplicates.items():
        detail = ", ".join(f"npi={r.fields['npi']} {r.fields['concours']} à {benin(r.end)}" for r in items)
        print(f"  {number} x{len(items)} : {detail}")

    doubles = duplicate_registrations(requests)
    extra = sum(len(items) - 1 for items in doubles.values())
    heading(f"2. Inscriptions en double : {len(doubles)} candidats, {extra} inscriptions en trop")
    for (npi, exam), items in doubles.items():
        detail = ", ".join(f"{r.fields['numero']} à {benin(r.end)}" for r in items)
        print(f"  npi={npi} {exam} : {detail}")

    slowness = slow_identity_period(requests, identity_calls)
    heading("3. Lenteur du service d'identification (>= 5 s)")
    if slowness:
        print(f"  Période : du {benin(slowness['start'])} au {benin(slowness['end'])}")
        print(f"  Requêtes touchées : {slowness['requests']} (durée max de l'appel : {ms(slowness['max_duration'])})")
        for path_pattern, count in sorted(slowness["by_path"].items()):
            print(f"    {path_pattern} : {count}")
    else:
        print("  Aucune")

    closing = wrongful_closing_rejections(requests)
    heading(f"4. Refus « Inscriptions closes » à tort : {len(closing)} requêtes, "
            f"{len({r.fields['npi'] for r in closing})} candidats")
    for code in EXAMS:
        affected = [r for r in closing if r.fields["concours"] == code]
        if affected:
            print(f"  {code} (clôture {benin(EXAMS[code]['closing'])}) : {len(affected)} refus, "
                  f"du {benin(min(r.end for r in affected))} au {benin(max(r.end for r in affected))}")

    age = wrongful_age_rejections(requests)
    heading(f"5. Candidats refusés à tort pour l'âge : {len({r.fields['npi'] for r in age})}")
    for r in sorted(age, key=lambda r: r.end):
        print(f"  npi={r.fields['npi']} né(e) le {r.fields['naissance']} {r.fields['concours']} "
              f"centre={r.fields['centre']} le {benin(r.end)}")

    lists = center_list_durations(requests)
    heading("6. Liste des inscrits par centre")
    for center, items in lists.items():
        durations = [r.duration / timedelta(milliseconds=1) for r in items]
        rows = max(int(r.fields.get("lignes", 0)) for r in items)
        print(f"  {center} : {len(items)} appels, moyenne {mean(durations):.0f} ms, max {max(durations):.0f} ms, "
              f"jusqu'à {rows} lignes")

    suspicious = suspicious_searches(requests)
    heading(f"Bonus. Recherches suspectes (injection SQL) : {len(suspicious)}")
    for r in suspicious:
        print(f"  {benin(r.end)} {r.ip} -> {r.status} lignes={r.fields.get('lignes', '-')} "
              f"nom={unquote_plus(r.path.split('nom=', 1)[-1])}")


def main(args):
    if len(args) != 1:
        print("Usage : python3 analyze_log.py <chemin du journal>", file=sys.stderr)
        return 2
    path = Path(args[0])
    if not path.is_file():
        print(f"Fichier introuvable : {path}", file=sys.stderr)
        return 1
    report(path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
