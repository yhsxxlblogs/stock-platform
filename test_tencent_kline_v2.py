#!/usr/bin/env python3
"""
测试腾讯 K 线 API - 尝试不同格式
"""
import requests
import json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://stockpage.10jqka.com.cn"
}

def test_url(url, desc):
    """测试指定 URL"""
    print(f"\n{'='*60}")
    print(f"测试: {desc}")
    print(f"URL: {url}")
    print(f"{'='*60}")

    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"状态码: {response.status_code}")
        print(f"响应内容:\n{response.text[:800]}")

        if response.status_code == 200:
            data = response.json()
            code = data.get('code')
            msg = data.get('msg')
            print(f"\n解析: code={code}, msg={msg}")

            # 尝试获取 K 线数据
            for symbol_key in data.get("data", {}).keys():
                if symbol_key != "version":
                    klines = data.get("data", {}).get(symbol_key, {}).get("day", [])
                    print(f"K线数据条数: {len(klines)}")
                    if klines:
                        print(f"最新数据: {klines[-1]}")
                        return True
        return False

    except Exception as e:
        print(f"请求失败: {e}")
        return False

def main():
    symbol = "sh600519"

    # 尝试不同的 URL 格式
    urls = [
        (f"https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day", "格式1: 无数量限制"),
        (f"https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,20240101", "格式2: 带开始日期"),
        (f"https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,20240101,20241231", "格式3: 带日期范围"),
        (f"https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,20240101,20241231,100", "格式4: 完整参数"),
        (f"https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,,,100,qfq", "格式5: 带复权"),
        (f"https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,100", "格式6: 简化参数"),
    ]

    for url, desc in urls:
        test_url(url, desc)

if __name__ == "__main__":
    main()
