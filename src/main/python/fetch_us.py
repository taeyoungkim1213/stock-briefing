import sys
import json
import yfinance as yf

def fetch_stock(symbol, name, sector):
    try:
        ticker = yf.Ticker(symbol)
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
            "symbol": symbol,
            "price": f"{last_price:.2f}",
            "change": f"{sign}{change_val:.2f}",
            "changeRate": f"{sign}{rate_val:.2f}%",
            "sector": sector,
            "market": "US"
        }
    except Exception as e:
        print(f"[ERROR] 미국 종목 수집 실패 {symbol}: {e}", file=sys.stderr)
        return {
            "name": name, "symbol": symbol,
            "price": "N/A", "change": "N/A", "changeRate": "N/A",
            "sector": sector, "market": "US"
        }

def main():
    raw = sys.stdin.buffer.read().decode('utf-8-sig').strip()
    config = json.loads(raw)

    results = []
    for item in config.get("indices", []):
        results.append(fetch_stock(item["symbol"], item["name"], "INDEX"))
    for item in config.get("semiconductor", []):
        results.append(fetch_stock(item["symbol"], item["name"], "semiconductor"))
    for item in config.get("bio", []):
        results.append(fetch_stock(item["symbol"], item["name"], "bio"))

    print(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    main()
