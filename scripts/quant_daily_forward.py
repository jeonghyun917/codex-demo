"""Daily completed-session operator. Default: read-only plan; --run permits batch writes.

No model tuning, retrospective forecast creation, credential acquisition or scheduler installation.
The app automation invokes this controller and separately advances the primary-evidence queue.
"""
import argparse
from contextlib import contextmanager
import csv
from datetime import date, datetime, timedelta, timezone
import json
import os
from pathlib import Path
import subprocess
import uuid
from zoneinfo import ZoneInfo

import quant_forward_validation as forward
import quant_v9_blend_evaluate as candidate_forward
import quant_forward_admission as admission
import quant_forward_diagnostics as diagnostics

INDICES = ("DOW30", "SP500", "NASDAQ100")
ROOT = Path(__file__).resolve().parents[1]


def completed_session(rows, now):
    forward.validated_sessions(rows)
    local_day = now.astimezone(ZoneInfo("America/New_York")).date()
    # Match the provider's conservative exclusion of the current New York day.
    if local_day > date.fromisoformat(rows[-1]["session"]) + timedelta(days=3):
        raise ValueError("Exchange calendar expired; acquire official extension, do not guess sessions")
    candidates = [r for r in rows if date.fromisoformat(r["session"]) < local_day
                  and forward.timestamp(r["closeUtc"]) <= now]
    if not candidates:
        raise ValueError("No completed prior New York session in calendar")
    return candidates[-1]


def missing_indices(anchor, frozen):
    return [index for index in INDICES if (index, anchor) not in frozen]


def write_record(path, body):
    with Path(path).open("x", encoding="utf-8") as stream:
        json.dump(body, stream, indent=2, allow_nan=False)
        stream.write("\n")


@contextmanager
def collection_lock(path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as stream:
        stream.seek(0, os.SEEK_END)
        if stream.tell() == 0:
            stream.write(b"0")
            stream.flush()
        stream.seek(0)
        if os.name == "nt":
            import msvcrt
            try:
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError as error:
                raise BlockingIOError("Daily collection already running") from error
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            stream.seek(0)
            if os.name == "nt":
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


def require_provider_safe(denials, running):
    if int(denials) or int(running):
        raise RuntimeError("Provider denied/throttled in last24h or a batch is RUNNING; inspect, do not retry")


def check_provider(db):
    denials = db.query("SELECT COUNT(*) FROM api_call_log WHERE provider='yahoo-chart' AND status_code IN (403,429) AND requested_at>=NOW()-INTERVAL 24 HOUR")[0][0]
    running = db.query("SELECT COUNT(*) FROM yahoo_candle_batch_run WHERE status='RUNNING'")[0][0]
    require_provider_safe(denials, running)


def price_reuse_allowed(status, started_epoch, cutoff_epoch, required, fresh, attempted):
    return (status in ("COMPLETED", "COMPLETED_WITH_ERRORS") and float(started_epoch) >= cutoff_epoch
            and required <= fresh | attempted)


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def frozen_manifests(data):
    result, seen = [], {}
    for path in sorted(data.glob("quant-forward-validation-*/*/cohort*.json")):
        body = read(path)
        forward.verify_seal(body)
        forward.verify_seal(body["protocol"])
        for cohort in body["cohorts"]:
            key = cohort["indexCode"], cohort["anchorSession"]
            fingerprint = cohort["archiveInput"]["sha256"]
            if key in seen and seen[key] != fingerprint:
                raise ValueError("Conflicting frozen cohorts; never silently replace the original")
            seen[key] = fingerprint
        if body["cohorts"]:
            result.append((path, body))
    return result, set(seen)


def command(args, log, timeout=1800):
    with Path(log).open("x", encoding="utf-8") as stream:
        subprocess.run([str(x) for x in args], cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                       check=True, timeout=timeout)


class Database:
    def __init__(self):
        self.client = ROOT / ".tools/mariadb-11.8.2-winx64/bin/mariadb.exe"
        self.dump = self.client.with_name("mariadb-dump.exe")

    def query(self, sql):
        out = subprocess.run([str(self.client), "-h", "127.0.0.1", "-u", "root", "-N", "-B",
                              "king_yurina", "-e", sql], cwd=ROOT, check=True,
                             capture_output=True, text=True, encoding="utf-8").stdout
        return [line.split("\t") for line in out.splitlines()]

    def backup(self, output, anchor, signal_day):
        backup = output / "backup"
        backup.mkdir()
        scopes = {
            "stock_candle_daily": f"trade_date >= '{date.fromisoformat(anchor)-timedelta(days=370)}'",
            "stock_signal_latest": "1=1", "stock_data_quality_latest": "1=1",
            "stock_expected_return_calibration": "1=1", "stock_dashboard_view_snapshot": "1=1",
            "stock_macro_feature_snapshot": "1=1",
        }
        for table in ("stock_signal_snapshot", "stock_risk_snapshot", "stock_expected_return_snapshot",
                      "stock_factor_exposure_snapshot", "stock_expected_return_factor_contribution"):
            scopes[table] = f"signal_date = '{signal_day}'"
        for table in ("stock_covariance_snapshot", "stock_correlation_snapshot"):
            scopes[table] = f"snapshot_date = '{signal_day}'"
        for table in ("stock_market_snapshot", "stock_index_membership_snapshot", "stock_shares_outstanding_snapshot"):
            scopes[table] = f"snapshot_date >= (SELECT COALESCE(MIN(last_date),'2026-09-21') FROM (SELECT MAX(trade_date) last_date FROM stock_benchmark_return_series WHERE index_code IN ('DOW30','SP500','NASDAQ100') GROUP BY index_code) x)"
        scopes["stock_benchmark_return_series"] = "trade_date >= '2026-09-21'"
        records = []
        for table, where in scopes.items():
            file = backup / (table + ".sql")
            subprocess.run([str(self.dump), "-h", "127.0.0.1", "-u", "root", "--single-transaction",
                            "--quick", "--skip-lock-tables", "--no-create-info", "--skip-add-locks",
                            "--skip-disable-keys", "--complete-insert", "--where=" + where,
                            "--result-file=" + str(file), "king_yurina", table], check=True, cwd=ROOT)
            records.append(dict(table=table, where=where, sha256=forward.digest(file.read_bytes()), bytes=file.stat().st_size))
        write_record(backup / "manifest.json", dict(format="SCOPED_DATA_ONLY_NO_DDL", tables=records))


def freeze_available(protocol_root, archive, anchor):
    added = []
    for index in INDICES:
        _, known = frozen_manifests(ROOT/"data")
        if (index, anchor) in known:
            continue  # Preserve legacy seals; new rules apply only to new admissions.
        frozen = forward.freeze_cohort(read(protocol_root/index/"protocol.json"), archive)
        selected = [c for c in frozen["cohorts"] if c["anchorSession"] == anchor]
        if not selected:
            continue
        for cohort in selected:
            admission.require(cohort["archiveInput"]["path"], anchor, ROOT/"data/quant-forward-admission")
        path = protocol_root/index/("cohort-" + anchor + ".json")
        forward.write_immutable(path, frozen)
        added.append(str(path))
    return added


def outcome_requirements(manifests, anchor):
    """Exact frozen stock/index close keys; never infer unknown exchange days."""
    stock, benchmarks, known = set(), set(), set()
    for _, manifest in manifests:
        sessions = forward.validated_sessions(manifest["protocol"]["calendar"])
        known.update(row["session"] for row in sessions)
        for cohort in manifest["cohorts"]:
            days = [row["session"] for row in sessions
                    if cohort["entrySession"] <= row["session"] <= min(anchor, cohort["exitSession"])]
            stock.update((p["symbol"], day) for p in cohort["predictions"] for day in days)
            benchmarks.update((cohort["indexCode"], day) for day in days)
    return dict(stockKeys=stock, benchmarkKeys=benchmarks, knownSessions=sorted(known))


def outcome_missing(db, requirements):
    stock, benchmarks = requirements["stockKeys"], requirements["benchmarkKeys"]
    if not stock:
        return set(), set()
    import re
    symbols = sorted({symbol for symbol, _ in stock})
    if any(not re.fullmatch(r"[A-Z0-9.^_=-]{1,40}", symbol) for symbol in symbols):
        raise ValueError("Unsafe frozen security identifier")
    days = sorted({day for _, day in stock})
    where = "trade_date BETWEEN '" + days[0] + "' AND '" + days[-1] + "'"
    available = {(r[0], r[1]) for r in db.query("SELECT symbol,CAST(trade_date AS CHAR) FROM stock_candle_daily WHERE "
                 + where + " AND close_price>0 AND symbol IN (" + ",".join("'" + s + "'" for s in symbols) + ")")}
    available_bench = {(r[0], r[1]) for r in db.query("SELECT index_code,CAST(trade_date AS CHAR) FROM stock_benchmark_return_series WHERE "
                       + where + " AND benchmark_level>0")}
    return stock - available, benchmarks - available_bench


def maintain_outcomes(db, output, anchor, manifests, now):
    """Maintain labels independently of permission to create a new forecast."""
    requirements = outcome_requirements(manifests, anchor)
    missing_stock, missing_bench = outcome_missing(db, requirements)
    result = dict(status="COMPLETE_STORED_CLOSES" if requirements["stockKeys"] else "NOT_DUE",
                  throughSession=anchor, requiredStockCloses=len(requirements["stockKeys"]),
                  requiredBenchmarkCloses=len(requirements["benchmarkKeys"]),
                  priceCollection=False, benchmarkAppend=False, forecastGeneration=False)
    if not missing_stock and not missing_bench:
        return result
    check_provider(db)
    required = {symbol for symbol, _ in requirements["stockKeys"]}
    if missing_bench:
        required |= {r[0] for r in db.query("SELECT DISTINCT symbol FROM index_constituent WHERE current_member=TRUE AND index_code IN ('DOW30','SP500','NASDAQ100')")}
    cutoff = max((now-timedelta(hours=20)).timestamp(),
                 datetime.combine(date.fromisoformat(anchor)+timedelta(days=1), datetime.min.time(),
                                  ZoneInfo("America/New_York")).timestamp())
    recent_rows = db.query("SELECT status,UNIX_TIMESTAMP(started_at) FROM yahoo_candle_batch_run WHERE started_at>=NOW()-INTERVAL 20 HOUR ORDER BY id DESC LIMIT 1")
    fresh = {r[0] for r in db.query("SELECT symbol FROM stock_candle_daily WHERE trade_date='"+anchor+"'")}
    attempted = {r[0] for r in db.query("SELECT DISTINCT symbol FROM api_call_log WHERE provider='yahoo-chart' AND UNIX_TIMESTAMP(requested_at)>="+str(int(cutoff)))}
    reused = bool(recent_rows) and price_reuse_allowed(recent_rows[0][0], recent_rows[0][1], cutoff, required, fresh, attempted)
    if recent_rows and not reused:
        raise RuntimeError("Recent provider attempt does not establish held-label coverage; inspect, do not retry")
    # Existing fresh provider attempts may legitimately leave a terminal/missing
    # symbol. Reuse their evidence, never loop or fill a synthetic price.
    if not reused or missing_bench:
        maintenance = output/"outcome-maintenance"
        maintenance.mkdir()
        marker = ROOT/"data/quant-daily"/("outcome-attempt-"+anchor+".json")
        marker.parent.mkdir(parents=True, exist_ok=True)
        write_record(marker, dict(startedAtUtc=forward.iso(now), output=str(maintenance),
                                 retryPolicy="INSPECT_FAILURE_BEFORE_EXPLICIT_RETRY", forecastGeneration=False))
        db.backup(maintenance, anchor, now.astimezone(ZoneInfo("Asia/Seoul")).date().isoformat())
        import shutil
        maven = ROOT/"mvnw.cmd" if (ROOT/"mvnw.cmd").exists() else shutil.which("mvn.cmd")
        if not maven:
            raise RuntimeError("Maven unavailable for outcome-only operator")
        command([maven, "-Dmaven.repo.local=.m2/repository", "-DskipTests", "compile"], maintenance/"compile.log")
        cp = str(ROOT/"target/classes")+os.pathsep+(ROOT/"tmp/quant-runtime-classpath.txt").read_text().strip()
        if not reused:
            command(["java", "-Xmx1800m", "-Duser.timezone=Asia/Seoul", "-cp", cp,
                     "com.kingyurina.demo.CodexDemoApplication", "--spring.profiles.active=mariadb",
                     "--spring.sql.init.mode=never", "--spring.main.web-application-type=none",
                     "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false",
                     "--app.batch.yahoo-candle.enabled=true", "--app.batch.yahoo-candle.exit-on-complete=true",
                     "--app.batch.yahoo-candle.refresh-signals=false", "--app.batch.yahoo-candle.years=1",
                     "--app.batch.yahoo-candle.delay-millis=250", "--app.batch.yahoo-candle.symbols="+",".join(sorted(required))], maintenance/"prices.log")
            result["priceCollection"] = True
        check_provider(db)
        if missing_bench:
            write_record(maintenance/"request.json", dict(completedSession=anchor,
                         knownSessions=requirements["knownSessions"], indices=sorted({i for i, _ in missing_bench})))
            classes = maintenance/"operator-classes"
            classes.mkdir()
            command(["javac", "-cp", cp, "-d", classes, ROOT/"scripts/QuantMaintainForwardOutcomes.java"], maintenance/"operator-compile.log")
            command(["java", "-Duser.timezone=Asia/Seoul", "-cp", str(classes)+os.pathsep+cp,
                     "QuantMaintainForwardOutcomes", maintenance/"request.json", maintenance/"benchmark-result.json"], maintenance/"benchmarks.log")
            result["benchmarkAppend"] = True
    missing_stock, missing_bench = outcome_missing(db, requirements)
    result.update(status="INCOMPLETE_LABELS" if missing_stock or missing_bench else "COMPLETE_STORED_CLOSES",
                  reusedRecentPriceAttempt=bool(reused),
                  missingStockCloses=[dict(symbol=s, session=d) for s, d in sorted(missing_stock)],
                  missingBenchmarkCloses=[dict(indexCode=i, session=d) for i, d in sorted(missing_bench)])
    return result


def evaluate_existing(db, output, anchor, now=None):
    manifests, _ = frozen_manifests(ROOT/"data")
    earliest = min((c["entrySession"] for _, m in manifests for c in m["cohorts"]), default=anchor)
    symbols = sorted({p["symbol"] for _, m in manifests for c in m["cohorts"] for p in c["predictions"]})
    prices = output/"held-closes.csv"
    quote = lambda s: "'" + s.replace("'", "''") + "'"
    rows = db.query("SELECT symbol,CAST(trade_date AS CHAR),close_price FROM stock_candle_daily WHERE trade_date BETWEEN "
                    + quote(earliest) + " AND " + quote(anchor) + " AND symbol IN ("
                    + ",".join(map(quote, symbols or ["__NO_COHORT__"])) + ") ORDER BY symbol,trade_date")
    for index in INDICES:
        rows += db.query("SELECT 'EXPLICIT_STORED_BENCHMARK_"+index+"',CAST(trade_date AS CHAR),benchmark_level "
                         "FROM stock_benchmark_return_series WHERE index_code="+quote(index)
                         +" AND trade_date BETWEEN "+quote(earliest)+" AND "+quote(anchor)+" ORDER BY trade_date")
    with prices.open("x", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["symbol", "date", "close"])
        writer.writerows(rows)
    evaluations = []
    for i, (path, manifest) in enumerate(manifests):
        report = forward.evaluate(manifest, prices, as_of=now)
        target = output/("evaluation-"+str(i)+".json")
        forward.write_immutable(target, report)
        evaluations.append(dict(manifest=str(path), report=str(target), counts=report["counts"], metrics=report["metrics"]))
    return evaluations


def evaluate_all(db, output, anchor, now=None):
    """Share one immutable close export; keep baseline and candidate counts separate."""
    baseline = evaluate_existing(db, output, anchor, now)
    manifests, _ = frozen_manifests(ROOT/"data")
    diagnosis = diagnostics.diagnose(manifests, output/"held-closes.csv",
                                    ROOT/"data/quant-forward-events/registry.json", as_of=now)
    forward.write_immutable(output/"eligibility-diagnostics.json", diagnosis)
    candidates = []
    bundles = sorted((ROOT/"data").glob("quant-candidate-evaluation-*/*/bundle.json"))
    for i, path in enumerate(bundles):
        target = output/("candidate-evaluation-"+str(i)+".json")
        try:
            report = candidate_forward.evaluate(path, output/"held-closes.csv", as_of=now)
            item = {k: report[k] for k in ("status", "expectedNames", "evaluatedNames", "counts", "metrics", "pairedDeltas")}
        except Exception as error:
            report = forward.seal(dict(status="FAILED", errorType=type(error).__name__, message=str(error),
                                       classification="CANDIDATE_EVALUATION_REJECTED_NO_SCORE", promotion=False))
            item = {k: report[k] for k in ("status", "errorType", "message")}
        forward.write_immutable(target, report)
        candidates.append(dict(bundle=str(path), report=str(target), **item))
    return baseline, candidates


def execute(protocol_root, run=False, now=None):
    # An explicit clock remains fixed for synthetic tests. Live evaluation must
    # use its own phase time, after newly created cohorts have been frozen.
    fixed_now = now
    now = now or datetime.now(timezone.utc)
    protocols = {i: read(protocol_root/i/"protocol.json") for i in INDICES}
    for p in protocols.values():
        forward.verify_seal(p)
    sessions = protocols["SP500"]["calendar"]
    if any(p["calendar"] != sessions for p in protocols.values()):
        raise ValueError("Active index calendars differ")
    calendar_expired = False
    try:
        anchor = completed_session(sessions, now)["session"]
    except ValueError:
        # Every valid old cohort exits within its embedded calendar. Keep scoring
        # those dates, but never infer unknown new exchange sessions from weekdays.
        forward.validated_sessions(sessions)
        if forward.timestamp(sessions[-1]["closeUtc"]) > now:
            raise
        anchor = sessions[-1]["session"]
        calendar_expired = True
    _, frozen = frozen_manifests(ROOT/"data")
    missing = missing_indices(anchor, frozen)
    position = next(i for i, row in enumerate(sessions) if row["session"] == anchor)
    calendar_ready = not calendar_expired and position + 21 < len(sessions)
    plan = dict(asOfUtc=forward.iso(now), anchorSession=anchor, missingIndices=missing,
                priceAndForecastCollectionRequired=bool(missing) and calendar_ready,
                calendarAllowsNewCapture=calendar_ready, mode="RUN" if run else "READ_ONLY_PLAN",
                controllerSha256=forward.digest(Path(__file__).read_bytes()),
                candidateEvaluationBundles=[str(p) for p in sorted((ROOT/"data").glob("quant-candidate-evaluation-*/*/bundle.json"))],
                promotion=False, unattendedHostRequirement="PC and Codex running; local DB accessible")
    if not run:
        return plan
    with collection_lock(ROOT/"data/quant-daily/collection.lock"):
        output = ROOT/"data/quant-daily"/(now.strftime("%Y%m%dT%H%M%SZ")+"-"+uuid.uuid4().hex[:8])
        output.mkdir()
        write_record(output/"plan.json", plan)
        try:
            db = Database()
            if not calendar_ready:
                manifests, _ = frozen_manifests(ROOT/"data")
                plan["outcomeMaintenance"] = maintain_outcomes(db, output, anchor, manifests, now)
                evaluation_now = fixed_now or datetime.now(timezone.utc)
                plan["evaluationAsOfUtc"] = forward.iso(evaluation_now)
                plan["evaluations"], plan["candidateEvaluations"] = evaluate_all(db, output, anchor, evaluation_now)
                plan["status"] = ("FAILED_CANDIDATE_EVALUATION" if any(r["status"] == "FAILED" for r in plan["candidateEvaluations"])
                                  else "COLLECTION_BLOCKED_CALENDAR_EVALUATION_COMPLETED")
                plan["output"] = str(output)
                write_record(output/"result.json", plan)
                return plan
            plan["newFrozenManifests"] = freeze_available(protocol_root, ROOT/"data/quant-forward", anchor)
            _, frozen = frozen_manifests(ROOT/"data")
            missing = missing_indices(anchor, frozen)
            if missing:
                check_provider(db)
                marker = ROOT/"data/quant-daily"/("attempt-"+anchor+".json")
                write_record(marker, dict(startedAtUtc=forward.iso(now), output=str(output), retryPolicy="INSPECT_FAILURE_BEFORE_EXPLICIT_RETRY"))
                db.backup(output, anchor, now.astimezone(ZoneInfo("Asia/Seoul")).date().isoformat())
                cp = str(ROOT/"target/classes")+os.pathsep+(ROOT/"tmp/quant-runtime-classpath.txt").read_text().strip()
                # Build changed source in the same checkout without a second scheduler or schema initialization.
                maven = ROOT/"mvnw.cmd"
                if not maven.exists():
                    import shutil
                    maven = shutil.which("mvn.cmd")
                if not maven:
                    raise RuntimeError("Maven not available")
                command([maven, "-Dmaven.repo.local=.m2/repository", "-DskipTests", "compile"], output/"compile.log")
                operator = output/"operator-classes"
                operator.mkdir()
                command(["javac", "-cp", cp, "-d", operator, ROOT/"scripts/QuantStartForwardCollection.java"], output/"operator-compile.log")
                live_cp = str(ROOT/"target/classes")+os.pathsep+str(operator)+os.pathsep+(ROOT/"tmp/quant-runtime-classpath.txt").read_text().strip()
                command(["java", "-cp", live_cp, "QuantStartForwardCollection", "--verify-classpath"], output/"classpath.log")
                evidence = [ROOT/"scripts/QuantStartForwardCollection.java"]
                for name in ("SecFinancialStandardService", "StockSignalService", "StockExpectedReturnV8Service", "StockExpectedReturnV9Service"):
                    evidence += [ROOT/("src/main/java/com/kingyurina/demo/stock/"+name+".java"),
                                 ROOT/("target/classes/com/kingyurina/demo/stock/"+name+".class")]
                write_record(output/"execution-code.json", {str(p): forward.digest(p.read_bytes()) for p in evidence})
                current = {r[0] for r in db.query("SELECT DISTINCT symbol FROM index_constituent WHERE current_member=TRUE AND index_code IN ('DOW30','SP500','NASDAQ100')")}
                manifests, _ = frozen_manifests(ROOT/"data")
                held = {p["symbol"] for _, m in manifests for c in m["cohorts"] for p in c["predictions"]}
                required = current | held
                cutoff_epoch = max((now-timedelta(hours=20)).timestamp(),
                                   datetime.combine(date.fromisoformat(anchor)+timedelta(days=1), datetime.min.time(),
                                                    ZoneInfo("America/New_York")).timestamp())
                recent_rows = db.query("SELECT status,UNIX_TIMESTAMP(started_at) FROM yahoo_candle_batch_run WHERE started_at>=NOW()-INTERVAL 20 HOUR ORDER BY id DESC LIMIT 1")
                fresh = {r[0] for r in db.query("SELECT symbol FROM stock_candle_daily WHERE trade_date='"+anchor+"'")}
                attempted = {r[0] for r in db.query("SELECT DISTINCT symbol FROM api_call_log WHERE provider='yahoo-chart' AND UNIX_TIMESTAMP(requested_at)>="+str(int(cutoff_epoch)))}
                recent = bool(recent_rows) and price_reuse_allowed(recent_rows[0][0], recent_rows[0][1], cutoff_epoch, required, fresh, attempted)
                if recent_rows and not recent:
                    raise RuntimeError("Recent Yahoo attempt does not establish this session/held-symbol coverage; inspect before a second daily attempt")
                if not recent:
                    symbols = sorted(required)
                    command(["java", "-Xmx1800m", "-Duser.timezone=Asia/Seoul", "-cp", cp,
                             "com.kingyurina.demo.CodexDemoApplication", "--spring.profiles.active=mariadb",
                             "--spring.sql.init.mode=never", "--spring.main.web-application-type=none",
                             "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false",
                             "--app.batch.yahoo-candle.enabled=true", "--app.batch.yahoo-candle.exit-on-complete=true",
                             "--app.batch.yahoo-candle.refresh-signals=false", "--app.batch.yahoo-candle.years=1",
                             "--app.batch.yahoo-candle.delay-millis=250", "--app.batch.yahoo-candle.symbols="+",".join(symbols)], output/"prices.log")
                # A process exit0 can still mean COMPLETED_WITH_ERRORS after a403/429.
                check_provider(db)
                last_status = db.query("SELECT status FROM yahoo_candle_batch_run ORDER BY id DESC LIMIT 1")
                if not last_status or last_status[0][0] not in ("COMPLETED", "COMPLETED_WITH_ERRORS"):
                    raise RuntimeError("Yahoo collection did not complete; forecasts stopped")
                plan["reusedRecentPriceAttempt"] = bool(recent)
                # Do not let a partial provider failure silently produce a tiny apparent validation cohort.
                for index in INDICES:
                    listed, fresh = db.query("SELECT COUNT(*),SUM(EXISTS(SELECT 1 FROM stock_candle_daily c WHERE c.symbol=i.symbol AND c.trade_date='"+anchor+"')) FROM index_constituent i WHERE i.current_member=TRUE AND i.index_code='"+index+"'")[0]
                    if int(listed) < (20 if index=="DOW30" else 80 if index=="NASDAQ100" else 400) or int(fresh or 0)/int(listed) < .95:
                        raise RuntimeError("Insufficient fresh price coverage: "+index)
                command(["java", "-Xmx1800m", "-Duser.timezone=Asia/Seoul", "-cp", live_cp,
                         "QuantStartForwardCollection", anchor, output/"forward"], output/"forward.log")
                plan["newRunAdmissions"] = admission.register(output/"forward/eligibility-before-forecast.json",
                    output/"forward/result.json", ROOT/"data/quant-forward-admission")
                plan["newFrozenManifests"] += freeze_available(protocol_root, ROOT/"data/quant-forward", anchor)
                _, frozen = frozen_manifests(ROOT/"data")
                if missing_indices(anchor, frozen):
                    raise RuntimeError("Forecast batch did not produce every index-bound frozen cohort")
            else:
                manifests, _ = frozen_manifests(ROOT/"data")
                plan["outcomeMaintenance"] = maintain_outcomes(db, output, anchor, manifests, now)
            evaluation_now = fixed_now or datetime.now(timezone.utc)
            plan["evaluationAsOfUtc"] = forward.iso(evaluation_now)
            plan["evaluations"], plan["candidateEvaluations"] = evaluate_all(db, output, anchor, evaluation_now)
            plan["status"] = ("FAILED_CANDIDATE_EVALUATION" if any(r["status"] == "FAILED" for r in plan["candidateEvaluations"]) else "COMPLETED")
            plan["collectionSkippedAlreadyFrozen"] = not bool(missing)
            plan["output"] = str(output)
            write_record(output/"result.json", plan)
            return plan
        except Exception as error:
            failure = dict(status="FAILED", errorType=type(error).__name__, message=str(error), promotion=False)
            try:
                # Preserve partial create-only exports/reports from the failed
                # stage instead of hiding its error behind a filename collision.
                recovery_output = output/"failure-evaluation"
                recovery_output.mkdir()
                evaluation_now = fixed_now or datetime.now(timezone.utc)
                failure["evaluationAsOfUtc"] = forward.iso(evaluation_now)
                failure["evaluations"], failure["candidateEvaluations"] = evaluate_all(db, recovery_output, anchor, evaluation_now)
            except Exception as evaluation_error:
                failure["evaluationError"] = str(evaluation_error)
            write_record(output/"failure.json", failure)
            raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--publish-status", action="store_true", help="After successful evaluation, publish separate DB status snapshots")
    parser.add_argument("--protocol-root", type=Path, default=ROOT/"data/quant-forward-validation-20261031-v2")
    args = parser.parse_args(argv)
    if args.publish_status and not args.run:
        parser.error("--publish-status requires --run; read-only planning never writes DB")
    result = execute(args.protocol_root.resolve(), args.run)
    if args.publish_status and not result.get("status", "").startswith("FAILED"):
        import quant_validation_status
        output = Path(result["output"])
        publication = output/"status-publication.json"
        forward.write_immutable(publication, quant_validation_status.build(output/"result.json"))
        cp = str(ROOT/"target/classes")+os.pathsep+(ROOT/"tmp/quant-runtime-classpath.txt").read_text().strip()
        operator = output/"status-operator"
        operator.mkdir()
        command(["javac", "-cp", cp, "-d", operator, ROOT/"scripts/QuantPublishValidationStatus.java"], output/"status-compile.log")
        live_cp = str(ROOT/"target/classes")+os.pathsep+str(operator)+os.pathsep+(ROOT/"tmp/quant-runtime-classpath.txt").read_text().strip()
        command(["java", "-cp", live_cp, "QuantPublishValidationStatus", "--apply", publication,
                 output/"status-published"], output/"status-publish.log")
        result["statusPublicationReceipt"] = str(output/"status-published/receipt.json")
    print(json.dumps(result, indent=2, allow_nan=False))
    return 1 if result.get("status", "").startswith("FAILED") else 0


if __name__ == "__main__":
    raise SystemExit(main())
