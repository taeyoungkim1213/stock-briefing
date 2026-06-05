DOMESTIC_CONFIG = {
    "indices": [
        {"code": "KOSPI",  "name": "코스피"},
        {"code": "KOSDAQ", "name": "코스닥"},
    ],
    "sectors": {
        "semiconductor": [
            {"code": "005930", "name": "삼성전자"},
            {"code": "000660", "name": "SK하이닉스"},
            {"code": "042700", "name": "한미반도체"},
        ],
        "bio": [
            {"code": "207940", "name": "삼성바이오로직스"},
            {"code": "068270", "name": "셀트리온"},
            {"code": "000100", "name": "유한양행"},
        ],
        "battery": [
            {"code": "373220", "name": "LG에너지솔루션"},
            {"code": "006400", "name": "삼성SDI"},
            {"code": "005490", "name": "POSCO홀딩스"},
        ],
    },
}

US_CONFIG = {
    "indices": [
        {"symbol": "SPY",  "name": "S&P500 ETF"},
        {"symbol": "QQQ",  "name": "나스닥 ETF"},
    ],
    "semiconductor": [
        {"symbol": "NVDA", "name": "엔비디아"},
        {"symbol": "AMD",  "name": "AMD"},
        {"symbol": "SOXX", "name": "반도체 ETF"},
    ],
    "bio": [
        {"symbol": "XBI",  "name": "바이오 ETF"},
        {"symbol": "JNJ",  "name": "존슨앤존슨"},
    ],
}
