"""Bounded primary terminal-event evidence. Never imports prices or writes a DB.

Run once with --acquire; later --verify is offline and does not redownload.
All outputs are a new namespace. Current updated notices are NOT PIT captures.
"""
import argparse
from datetime import date, datetime, timezone
import hashlib
from html import unescape
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import urllib.request

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / "tmp/free-quant-extra-20261018"

# Values are explicitly curated from primary pages. Assertions below bind them
# to saved source text; there is no guess of market price or terminal return.
SPECS = [
    dict(id="cern-nasdaq", publisher="Nasdaq Trader", symbol="CERN",
         url="https://www.nasdaqtrader.com/TraderNews.aspx?id=eca2022-127",
         publishedDate=None, initialPageDate="2022-06-03",
         assertions=["156782104", "$95.00", "June 7, 2022", "June 9, 2022", "June 8, 2022"],
         event=dict(action="CASH_MERGER", effectiveDate="2022-06-08", cashPerShare="95.00", currency="USD",
                    cusip="156782104", lastTradingDate="2022-06-07", suspensionDate="2022-06-09",
                    effectiveTiming="BEFORE_MARKET_OPEN")),
    dict(id="mxim-nasdaq", publisher="Nasdaq Trader", symbol="MXIM",
         url="https://www.nasdaqtrader.com/TraderNews.aspx?id=ECA2021-172",
         publishedDate=None, initialPageDate="2021-08-23",
         assertions=["57772K101", "0.630", "August 25, 2021", "August 27, 2021", "August 26, 2021"],
         event=dict(action="STOCK_MERGER", effectiveDate="2021-08-26", receivedSymbol="ADI",
                    sharesPerShare="0.630", cusip="57772K101", lastTradingDate="2021-08-25",
                    suspensionDate="2021-08-27", effectiveTiming="BEFORE_MARKET_OPEN")),
    dict(id="sgen-pfizer", publisher="Pfizer", symbol="SGEN",
         url="https://www.pfizer.com/news/press-release/press-release-detail/pfizer-completes-acquisition-seagen",
         publishedDate="2023-12-14", assertions=["December 14, 2023", "$229", "completed"],
         event=dict(action="CASH_MERGER", effectiveDate="2023-12-14", cashPerShare="229", currency="USD")),
    dict(id="splk-cisco", publisher="Cisco", symbol="SPLK",
         url="https://newsroom.cisco.com/c/r/newsroom/en/us/a/y2024/m03/cisco-completes-acquisition-of-splunk.html",
         publishedDate="2024-03-18", assertions=["March 18, 2024", "$157", "completed"],
         event=dict(action="CASH_MERGER", effectiveDate="2024-03-18", cashPerShare="157", currency="USD")),
    dict(id="xlnx-amd", publisher="AMD", symbol="XLNX",
         url="https://www.amd.com/en/newsroom/press-releases/2022-2-14-amd-completes-acquisition-of-xilinx.html",
         publishedDate="2022-02-14", assertions=["Feb. 14, 2022", "1.7234", "fractional shares"],
         event=dict(action="STOCK_MERGER", effectiveDate="2022-02-14", receivedSymbol="AMD", sharesPerShare="1.7234",
                    fractionalShareCash="PAYMENT_EXISTS_AMOUNT_AND_SETTLEMENT_NOT_ACQUIRED")),
    dict(id="abmd-jnj", publisher="Johnson & Johnson", symbol="ABMD",
         url="https://www.jnj.com/media-center/press-releases/johnson-johnson-completes-acquisition-of-abiomed",
         publishedDate="2022-12-22", assertions=["December 22, 2022", "$380.00", "$35.00", "contingent value right"],
         event=dict(action="CASH_PLUS_CVR_MERGER", effectiveDate="2022-12-22", cashPerShare="380.00", currency="USD",
                    contingentAdditionalCashMaximum="35.00", cvrStatus="CONDITIONAL_NOT_GUARANTEED_NOT_TERMINAL_CASH")),
    dict(id="dre-prologis", publisher="Prologis issuer-hosted SEC filing", symbol="DRE",
         url="https://ir.prologis.com/financials/sec-filings/content/0001564590-22-035526/pld-10q_20220930.htm",
         publishedDate="2022-10-28", assertions=["October 3, 2022", "Duke Realty", "0.475"],
         event=dict(action="STOCK_MERGER", effectiveDate="2022-10-03", receivedSymbol="PLD", sharesPerShare="0.475",
                    knowledgeWarning="THIS_CONFIRMATION_WAS_FILED_AFTER_EFFECTIVE_DATE")),
    dict(id="frc-fdic", publisher="FDIC", symbol="FRC",
         url="https://www.fdic.gov/news/press-releases/2023/pr23034.html",
         publishedDate="2023-05-01", assertions=["May 1, 2023", "First Republic Bank", "receiver"],
         event=dict(action="BANK_RECEIVERSHIP", effectiveDate="2023-05-01",
                    recoveryPerShare=None, recoveryStatus="NOT_ESTABLISHED_DO_NOT_INFER_FROM_DEPOSITOR_PROTECTION")),
]

# Different primary records, not retries of blocked issuer pages. The initial
# acquisition and its failure records remain unchanged.
SUPPLEMENT = [
    dict(id="abmd-sec", publisher="SEC / JNJ filed press release", symbol="ABMD",
         url="https://www.sec.gov/Archives/edgar/data/200406/000119312522311072/d428734dex991.htm",
         publishedDate="2022-12-22", assertions=["December 22, 2022", "$380.00", "$35.00", "contingent value right"],
         event=dict(action="CASH_PLUS_CVR_MERGER", effectiveDate="2022-12-22", cashPerShare="380.00", currency="USD",
                    contingentAdditionalCashMaximum="35.00", cvrStatus="CONDITIONAL_NOT_GUARANTEED_NOT_TERMINAL_CASH")),
    dict(id="sgen-sec", publisher="SEC / Pfizer filed earnings release", symbol="SGEN",
         url="https://www.sec.gov/Archives/edgar/data/78003/000007800324000017/pfe-12312023xex99.htm",
         publishedDate="2024-01-30", assertions=["December 14, 2023", "$229 per share in cash", "January 30, 2024"],
         event=dict(action="CASH_MERGER", effectiveDate="2023-12-14", cashPerShare="229", currency="USD",
                    knowledgeWarning="THIS_CONFIRMATION_WAS_FILED_AFTER_EFFECTIVE_DATE")),
]


class TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}:
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in {"script", "style"}:
            self.hidden = max(0, self.hidden - 1)

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def plain_text(raw):
    parser = TextExtractor()
    parser.feed(raw.decode("utf-8", errors="replace"))
    return re.sub(r"\s+", " ", unescape(" ".join(parser.parts))).strip()


def validate_assertions(raw, assertions):
    text = plain_text(raw).casefold()
    missing = [item for item in assertions if item.casefold() not in text]
    if missing:
        raise ValueError("Source assertion absent: " + repr(missing))


def make_event(spec):
    event = dict(spec["event"], symbol=spec["symbol"], sourceId=spec["id"],
                 dateBasis="LEGAL_COMPLETION_OR_RECEIVERSHIP_DATE_NOT_SETTLEMENT_DATE",
                 announcementKnowledge="NOT_CERTIFIED_PIT",
                 amountAndRatioBasis="AS_TRANSACTED_NOT_RETROACTIVELY_SPLIT_ADJUSTED",
                 applicability="ELIGIBLE_COMMON_SHARE_ONLY_SOURCE_EXCEPTIONS_REMAIN",
                 priceUse="EVIDENCE_ONLY_NOT_OHLCV_NOT_RETURN_NOT_AUTO_IMPORT")
    date.fromisoformat(event["effectiveDate"])
    for field in ("cashPerShare", "sharesPerShare", "contingentAdditionalCashMaximum"):
        if field in event and float(event[field]) <= 0:
            raise ValueError("Nonpositive consideration")
    if event.get("lastTradingDate") and event["lastTradingDate"] >= event["effectiveDate"]:
        raise ValueError("Trading date must precede completion for this sample")
    return event


def write_new(path, data):
    with path.open("xb") as target:
        target.write(data)


def json_bytes(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def acquire(root, specs=SPECS):
    if (root / "manifest.json").exists():
        raise ValueError("Existing acquisition: use --verify; no redownload/overwrite")
    (root / "raw").mkdir(parents=True, exist_ok=True)
    sources, failures, events = [], [], []
    for spec in specs:
        path = root / "raw" / (spec["id"] + ".html")
        if path.exists():
            raise ValueError("Refusing to overwrite " + str(path))
        retrieved = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        try:
            request = urllib.request.Request(spec["url"], headers={"User-Agent": "Mozilla/5.0 (public source research)"})
            with urllib.request.urlopen(request, timeout=25) as response:
                raw = response.read(8_000_001)
                if len(raw) > 8_000_000:
                    raise ValueError("Source exceeds bounded download size")
                source = dict(id=spec["id"], symbol=spec["symbol"], publisher=spec["publisher"], url=spec["url"],
                              finalUrl=response.url, httpStatus=response.status, contentType=response.headers.get("Content-Type"),
                              retrievedAt=retrieved, publishedDate=spec.get("publishedDate"),
                              initialPageDate=spec.get("initialPageDate"), publishedDatePrecision="DAY_OR_UNKNOWN_UPDATED_NOTICE",
                              sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw), path="raw/" + path.name,
                              assertions=spec["assertions"], rights="PUBLIC_ACCESS_NOT_OPEN_REDISTRIBUTION_LICENSE")
            write_new(path, raw)
            sources.append(source)
            validate_assertions(raw, spec["assertions"])
            events.append(make_event(spec))
            print(spec["id"], "ACQUIRED_AND_ASSERTED", len(raw), flush=True)
        except (OSError, ValueError) as exc:
            failures.append(dict(id=spec["id"], url=spec["url"], attemptedAt=retrieved, error=str(exc)))
            print(spec["id"], "FAILED", str(exc), flush=True)
    bundle = dict(coverage="PARTIAL_PRIMARY_TERMINAL_EVENT_EVIDENCE", sources=sources, events=events,
                  warning="No missing daily prices recovered; no portfolio settlements inferred; no complete PIT universe established.")
    write_new(root / "bundle.json", json_bytes(bundle))
    manifest = dict(acquiredAt=datetime.now(timezone.utc).isoformat(), requestedSources=len(specs),
                    sources=sources, failures=failures, events=len(events),
                    bundleSha256=hashlib.sha256((root / "bundle.json").read_bytes()).hexdigest())
    write_new(root / "manifest.json", json_bytes(manifest))


def verify(root):
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    raw_bundle = (root / "bundle.json").read_bytes()
    if hashlib.sha256(raw_bundle).hexdigest() != manifest["bundleSha256"]:
        raise ValueError("Bundle hash mismatch")
    bundle = json.loads(raw_bundle)
    specs = {s["id"]: s for s in SPECS + SUPPLEMENT}
    sources = {s["id"]: s for s in manifest["sources"]}
    if len(sources) != len(manifest["sources"]) or bundle["sources"] != manifest["sources"]:
        raise ValueError("Duplicate or conflicting source identity")
    for source in sources.values():
        path = (root / source["path"]).resolve()
        if not path.is_relative_to(root.resolve()):
            raise ValueError("Unsafe source path")
        raw = path.read_bytes()
        if len(raw) != source["bytes"] or hashlib.sha256(raw).hexdigest() != source["sha256"]:
            raise ValueError("Source hash/size mismatch")
        if source["url"] != specs[source["id"]]["url"]:
            raise ValueError("Unexpected source URL")
    seen = set()
    for event in bundle["events"]:
        sid = event["sourceId"]
        if sid in seen or sid not in sources or event != make_event(specs[sid]):
            raise ValueError("Duplicate/orphan/altered event")
        seen.add(sid)
        validate_assertions((root / sources[sid]["path"]).read_bytes(), specs[sid]["assertions"])
    # Confirm these really address former missing-provider symbols, without DB access.
    previous = json.loads((REPO / "tmp/pit-acquisition/actions/manifest.json").read_text(encoding="utf-8"))
    failed_symbols = {r["symbol"] for r in previous["results"] if r["status"] != "OK"}
    event_symbols = {e["symbol"] for e in bundle["events"]}
    if not event_symbols <= failed_symbols:
        raise ValueError("Sample not tied to prior missing-symbol inventory")
    result = dict(status="VERIFIED", sources=len(sources), bytes=sum(s["bytes"] for s in sources.values()),
                  events=len(bundle["events"]), previouslyMissingSymbols=sorted(event_symbols),
                  failures=manifest["failures"], manifestSha256=hashlib.sha256((root / "manifest.json").read_bytes()).hexdigest(),
                  bundleSha256=manifest["bundleSha256"], verifiedAt=datetime.now(timezone.utc).isoformat())
    target = root / "verification.json"
    if not target.exists():
        write_new(target, json_bytes(result))
    print(json.dumps(result, indent=2))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--acquire", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--supplement", action="store_true", help="Use separately preserved SEC supplement namespace")
    args = parser.parse_args()
    root = ROOT / "sec-supplement" if args.supplement else ROOT
    if args.acquire:
        acquire(root, SUPPLEMENT if args.supplement else SPECS)
    if args.verify:
        verify(root)
