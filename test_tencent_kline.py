#!/usr/bin/env python3
"""
测试腾讯 K 线 API
"""
import requests
import json

TENCENT_KLINE_API = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://stockpage.10jqka.com.cn"
}

def test_tencent_kline(symbol="sh600519", kline_type="day", limit=100):
    """测试腾讯 K 线 API"""
    print(f"\n{'='*60}")
    print(f"测试腾讯 K 线 API")
    print(f"{'='*60}")

    # 构建 URL（按照后端代码的格式）
    url = f"{TENCENT_KLINE_API}?param={symbol},{kline_type},,,{limit}"
    print(f"请求 URL: {url}")

    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"状态码: {response.status_code}")
        print(f"响应内容前 500 字符:\n{response.text[:500]}")

        if response.status_code == 200:
            data = response.json()
            print(f"\n解析后的数据:")
            print(f"  code: {data.get('code')}")
            print(f"  msg: {data.get('msg')}")

            klines = data.get("data", {}).get(symbol, {}).get(kline_type, [])
            print(f"  K线数据条数: {len(klines)}")

            if klines:
                print(f"\n最新 3 条数据:")
                for i, item in enumerate(klines[-3:]):
                    print(f"  {i+1}. 日期: {item[0]}, 开盘: {item[1]}, 收盘: {item[2]}, 最低: {item[3]}, 最高: {item[4]}, 成交量: {item[5]}")
                return True
            else:
                print("警告: 没有获取到 K 线数据")
                return False

    except Exception as e:
        print(f"请求失败: {e}")
        return False

def test_tencent_kline_v2(symbol="sh600519", kline_type="day", limit=100):
    """测试腾讯 K 线 API（备选格式）"""
    print(f"\n{'='*60}")
    print(f"测试腾讯 K 线 API（备选格式）")
    print(f"{'='*60}")

    # 备选 URL 格式
    url = f"{TENCENT_KLINE_API}?param={symbol},{kline_type}"
    print(f"请求 URL: {url}")

    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"状态码: {response.status_code}")
        print(f"响应内容前 500 字符:\n{response.text[:500]}")

        if response.status_code == 200:
            data = response.json()
            klines = data.get("data", {}).get(symbol, {}).get(kline_type, [])
            print(f"  K线数据条数: {len(klines)}")
            return len(klines) > 0

    except Exception as e:
        print(f"请求失败: {e}")
        return False

def main():
    print("开始测试腾讯 K 线 API...")

    # 测试日线
    result1 = test_tencent_kline("sh600519", "day", 100)

    # 测试备选格式
    result2 = test_tencent_kline_v2("sh600519", "day", 100)

    print(f"\n{'='*60}")
    print("测试结果总结")
    print(f"{'='*60}")
    print(f"标准格式: {'✓ 正常' if result1 else '✗ 失败'}")
    print(f"备选格式: {'✓ 正常' if result2 else '✗ 失败'}")

if __name__ == "__main__":
    main()
