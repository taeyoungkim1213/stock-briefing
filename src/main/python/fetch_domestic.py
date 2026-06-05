import sys
import json
from datetime import datetime, timedelta

# pykrx import 시 stdout 출력("KRX 로그인 실패" 등)을 stderr로 전환
_orig_stdout = sys.stdout
sys.stdout = sys.stderr
from pykrx import stock
import yfinance as yf
sys.stdout = _orig_stdout

INDEX_YAHOO_MAP = {
    "KOSPI": "^KS11",
    "KOSDAQ": "^KQ11",
}

def recent_date_range():
    to_date = datetime.now().strftime("%Y%m%d")
    from_date = (datetime.now() - timedelta(days=7)).strftime("%Y%m%d")
    return from_date, to_date

def fmt_change(val):
    if val is None:
        return "N/A"
    sign = "+" if val >= 0 else ""
    return f"{sign}{val:,.2f}"

def fmt_rate(val):
    if val is None:
        return "N/A"
    sign = "+" if val >= 0 else ""
    return f"{sign}{val:.2f}%"

def fetch_index(code, name):
    """지수 데이터 수집 - yfinance 사용 (^KS11, ^KQ11)"""
    try:
        yahoo_symbol = INDEX_YAHOO_MAP.get(code, code)
        ticker = yf.Ticker(yahoo_symbol)
        info = ticker.fast_info

        last_price = info.last_price
        prev_close = info.previous_close

        if last_price is None or prev_close is None:
            raise ValueError("가격 데이터 없음")

        change_val = last_price - prev_close
        rate_val = (change_val / prev_close) * 100
        sign = "+" if change_val >= 0 else ""

        return {
            "name": name,
            "symbol": code,
            "price": f"{last_price:,.2f}",
            "change": f"{sign}{change_val:,.2f}",
            "changeRate": f"{sign}{rate_val:.2f}%",
            "sector": "INDEX",
            "market": "KR"
        }
    except Exception as e:
        print(f"[ERROR] 지수 수집 실패 {name}({code}): {e}", file=sys.stderr)
        return {"name": name, "symbol": code, "price": "N/A", "change": "N/A", "changeRate": "N/A", "sector": "INDEX", "market": "KR"}

def fetch_stock(code, name, sector):
    """개별 종목 수집 - pykrx 사용"""
    try:
        from_date, to_date = recent_date_range()
        df = stock.get_market_ohlcv_by_date(from_date, to_date, code)
        if df.empty:
            print(f"[WARN] 종목 데이터 없음: {name}({code})", file=sys.stderr)
            return {"name": name, "symbol": code, "price": "N/A", "change": "N/A", "changeRate": "N/A", "sector": sector, "market": "KR"}

        row = df.iloc[-1]
        close = row['종가']
        price = f"{int(close):,}"

        change_val = row.get('등락', close - row['시가'])
        rate_val = row.get('등락률', None)

        return {
            "name": name,
            "symbol": code,
            "price": price,
            "change": fmt_change(change_val),
            "changeRate": fmt_rate(rate_val),
            "sector": sector,
            "market": "KR"
        }
    except Exception as e:
        print(f"[ERROR] 종목 수집 실패 {name}({code}): {e}", file=sys.stderr)
        return {"name": name, "symbol": code, "price": "N/A", "change": "N/A", "changeRate": "N/A", "sector": sector, "market": "KR"}

def main():
    raw = sys.stdin.buffer.read().decode('utf-8-sig').strip()
    config = json.loads(raw)

    results = []

    for idx in config.get("indices", []):
        results.append(fetch_index(idx["code"], idx["name"]))

    for sector, items in config.get("sectors", {}).items():
        for item in items:
            results.append(fetch_stock(item["code"], item["name"], sector))

    print(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    main()
