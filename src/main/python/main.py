import json
import os
import subprocess
import sys
from datetime import datetime

import requests
from bs4 import BeautifulSoup

from stock_config import DOMESTIC_CONFIG, US_CONFIG

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

SECTOR_NAMES = {
    "INDEX": "지수",
    "semiconductor": "반도체",
    "bio": "바이오",
    "battery": "2차전지",
}

CLAUDE_PROMPT = """\
당신은 10년 경력의 전문 주식 애널리스트입니다.
아래 시장 데이터와 뉴스를 바탕으로 오늘의 브리핑을 작성해주세요.
투자 공부 중인 개인 투자자를 위해 핵심만 간결하게, 전문 용어는 쉽게 풀어서 작성해주세요.

[국내 시장 데이터]
{domesticData}

[미국 시장 데이터]
{usData}

[오늘의 주요 뉴스]
{newsData}

[브리핑 형식]
📊 오늘의 주식 브리핑 - {date}

🇰🇷 국내 시장
- 시장 분위기 (코스피/코스닥 흐름 2줄)
- 반도체 섹터: 동향 1줄
- 바이오 섹터: 동향 1줄
- 2차전지 섹터: 동향 1줄

🇺🇸 미국 시장
- 지수 흐름 1줄
- 반도체/바이오 동향 1줄

📰 오늘의 핵심 뉴스
- 주요 뉴스 2~3줄 요약

⚠️ 오늘의 리스크 포인트
- 주의해야 할 점 1~2줄

💡 공부 포인트
- 오늘 시장 흐름에서 배울 수 있는 것 1~2줄
"""


# ── 데이터 수집 ─────────────────────────────────────────────────────────────

def run_script(script_name, config):
    path = os.path.join(SCRIPT_DIR, script_name)
    try:
        child_env = os.environ.copy()
        child_env['PYTHONIOENCODING'] = 'utf-8'
        child_env['PYTHONUTF8'] = '1'

        result = subprocess.run(
            [sys.executable, path],
            input=json.dumps(config, ensure_ascii=False).encode('utf-8'),
            capture_output=True, timeout=90,
            env=child_env,
        )
        if result.returncode != 0:
            print(f"[WARN] {script_name} 비정상 종료: {result.stderr.decode('utf-8', errors='replace')[:300]}",
                  file=sys.stderr)
            return []
        return json.loads(result.stdout.decode('utf-8'))
    except Exception as e:
        print(f"[WARN] {script_name} 실패: {e}", file=sys.stderr)
        return []


def get_news():
    try:
        url = ("https://finance.naver.com/news/news_list.naver"
               "?mode=LSS2D&section_id=101&section_id2=258")
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                  "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                   "Accept-Language": "ko-KR,ko;q=0.9"}
        resp = requests.get(url, headers=headers, timeout=15)
        soup = BeautifulSoup(resp.text, "html.parser")
        titles = soup.select("dl.newsList dt.articleSubject a")
        if not titles:
            titles = soup.select(".articleSubject a")
        return [t.text.strip() for t in titles if t.text.strip()][:5]
    except Exception as e:
        print(f"[WARN] 뉴스 수집 실패: {e}", file=sys.stderr)
        return []


# ── 카카오 ──────────────────────────────────────────────────────────────────

def get_kakao_access_token():
    resp = requests.post(
        "https://kauth.kakao.com/oauth/token",
        data={
            "grant_type":    "refresh_token",
            "client_id":     os.environ["KAKAO_REST_API_KEY"],
            "client_secret": os.environ["KAKAO_CLIENT_SECRET"],
            "refresh_token": os.environ["KAKAO_REFRESH_TOKEN"],
        },
        timeout=15,
    )
    data = resp.json()
    if "error" in data:
        raise RuntimeError(f"카카오 토큰 갱신 실패: {data}")

    new_refresh = data.get("refresh_token")
    if new_refresh:
        print("[INFO] 새 refresh_token 발급됨 → GitHub Secret 자동 업데이트 시도", file=sys.stderr)
        try:
            subprocess.run(
                ["gh", "secret", "set", "KAKAO_REFRESH_TOKEN", "--body", new_refresh],
                check=True, timeout=15,
            )
            print("[INFO] KAKAO_REFRESH_TOKEN Secret 업데이트 완료", file=sys.stderr)
        except Exception as e:
            print(f"[WARN] Secret 자동 업데이트 실패 (수동 업데이트 필요): {e}", file=sys.stderr)

    return data["access_token"]


def send_kakao_message(text, access_token):
    truncated = text[:997] + "..." if len(text) > 1000 else text
    template = {
        "object_type": "text",
        "text": truncated,
        "link": {
            "web_url":        "https://finance.naver.com",
            "mobile_web_url": "https://finance.naver.com",
        },
    }
    resp = requests.post(
        "https://kapi.kakao.com/v2/api/talk/memo/default/send",
        headers={"Authorization": f"Bearer {access_token}"},
        data={"template_object": json.dumps(template, ensure_ascii=False)},
        timeout=15,
    )
    if not resp.ok:
        raise RuntimeError(f"카카오 메시지 전송 실패: {resp.status_code} {resp.text}")
    print(f"[INFO] 카카오톡 전송 완료: {resp.json()}", file=sys.stderr)


# ── Gemini ──────────────────────────────────────────────────────────────────

def format_stock_items(items, is_kr):
    grouped = {}
    for item in items:
        grouped.setdefault(item["sector"], []).append(item)

    lines = []
    for sector, stocks in grouped.items():
        lines.append(f"[{SECTOR_NAMES.get(sector, sector)}]")
        for s in stocks:
            if is_kr:
                lines.append(f"{s['name']}: {s['price']} ({s['change']}, {s['changeRate']})")
            else:
                lines.append(f"{s['name']}({s['symbol']}): {s['price']} ({s['change']}, {s['changeRate']})")
        lines.append("")
    return "\n".join(lines).strip() or "데이터 없음"


def call_gemini(domestic, us, news):
    date_str = datetime.now().strftime("%Y년 %m월 %d일")
    news_text = "\n".join(f"- {n}" for n in news) if news else "뉴스 데이터 없음"

    prompt = (CLAUDE_PROMPT
              .replace("{domesticData}", format_stock_items(domestic, is_kr=True))
              .replace("{usData}",      format_stock_items(us,       is_kr=False))
              .replace("{newsData}",    news_text)
              .replace("{date}",        date_str))

    resp = requests.post(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-05-20:generateContent",
        params={"key": os.environ["GEMINI_API_KEY"]},
        headers={"Content-Type": "application/json"},
        json={
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {"maxOutputTokens": 1024},
        },
        timeout=60,
    )
    data = resp.json()
    if "error" in data:
        raise RuntimeError(f"Gemini API 오류: {data['error']['message']}")
    return data["candidates"][0]["content"]["parts"][0]["text"]


# ── 메인 ────────────────────────────────────────────────────────────────────

def main():
    print(f"[INFO] 주식 브리핑 시작: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", file=sys.stderr)

    domestic = run_script("fetch_domestic.py", DOMESTIC_CONFIG)
    print(f"[INFO] 국내 종목 {len(domestic)}개 수집", file=sys.stderr)

    us = run_script("fetch_us.py", US_CONFIG)
    print(f"[INFO] 미국 종목 {len(us)}개 수집", file=sys.stderr)

    news = get_news()
    print(f"[INFO] 뉴스 {len(news)}개 수집", file=sys.stderr)

    briefing = call_gemini(domestic, us, news)
    print(f"[INFO] Gemini 분석 완료 ({len(briefing)}자)", file=sys.stderr)

    access_token = get_kakao_access_token()
    send_kakao_message(briefing, access_token)

    print("[INFO] 완료!", file=sys.stderr)


if __name__ == "__main__":
    main()
