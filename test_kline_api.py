#!/usr/bin/env python3
"""
测试 K 线 API 数据获取
"""
import requests
import json

# 东方财富 K 线 API
EASTMONEY_KLINE_API = "https://push2his.eastmoney.com/api/qt/stock/kline/get"

# 腾讯 K 线 API
TENCENT_KLINE_API = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
}

def test_eastmoney_kline(symbol="600519"):
    """测试东方财富 K 线 API"""
    print("=" * 60)
    print(f"测试东方财富 K 线 API - 股票: {symbol}")
    print("=" * 60)

    # 转换股票代码格式
    if symbol.startswith("6"):
        secid = f"1.{symbol}"
    else:
        secid = f"0.{symbol}"

    url = f"{EASTMONEY_KLINE_API}?secid={secid}&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=101&fqt=0&end=20500101&lmt=10"

    print(f"请求 URL: {url}")

    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"状态码: {response.status_code}")

        if response.status_code == 200:
            data = response.json()
            klines = data.get("data", {}).get("klines", [])

            print(f"\n获取到 {len(klines)} 条 K 线数据")

            if klines:
                print("\n最新 3 条数据:")
                for i, kline in enumerate(klines[-3:]):
                    parts = kline.split(",")
                    print(f"  {i+1}. 日期: {parts[0]}, 开盘: {parts[1]}, 收盘: {parts[2]}, 最高: {parts[3]}, 最低: {parts[4]}, 成交量: {parts[5]}")
                return True
            else:
                print("警告: 没有获取到 K 线数据")
                return False
        else:
            print(f"错误: HTTP {response.status_code}")
            return False

    except Exception as e:
        print(f"请求失败: {e}")
        return False

def test_tencent_kline(symbol="600519"):
    """测试腾讯 K 线 API"""
    print("\n" + "=" * 60)
    print(f"测试腾讯 K 线 API - 股票: {symbol}")
    print("=" * 60)

    # 转换股票代码格式
    if symbol.startswith("6"):
        tencent_symbol = f"sh{symbol}"
    else:
        tencent_symbol = f"sz{symbol}"

    url = f"{TENCENT_KLINE_API}?param={tencent_symbol},day,,,10"

    print(f"请求 URL: {url}")

    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"状态码: {response.status_code}")

        if response.status_code == 200:
            data = response.json()
            klines = data.get("data", {}).get(tencent_symbol, {}).get("day", [])

            print(f"\n获取到 {len(klines)} 条 K 线数据")

            if klines:
                print("\n最新 3 条数据:")
                for i, kline in enumerate(klines[-3:]):
                    print(f"  {i+1}. 日期: {kline[0]}, 开盘: {kline[1]}, 收盘: {kline[2]}, 最低: {kline[3]}, 最高: {kline[4]}, 成交量: {kline[5]}")
                return True
            else:
                print("警告: 没有获取到 K 线数据")
                print(f"响应内容: {json.dumps(data, indent=2)[:500]}")
                return False
        else:
            print(f"错误: HTTP {response.status_code}")
            return False

    except Exception as e:
        print(f"请求失败: {e}")
        return False

def main():
    print("开始测试 K 线 API...\n")

    # 测试贵州茅台
    symbol = "600519"

    # 测试东方财富
    eastmoney_ok = test_eastmoney_kline(symbol)

    # 测试腾讯
    tencent_ok = test_tencent_kline(symbol)

    print("\n" + "=" * 60)
    print("测试结果总结")
    print("=" * 60)
    print(f"东方财富 API: {'✓ 正常' if eastmoney_ok else '✗ 失败'}")
    print(f"腾讯 API: {'✓ 正常' if tencent_ok else '✗ 失败'}")

    if eastmoney_ok or tencent_ok:
        print("\n结论: 至少有一个 API 可以正常获取 K 线数据")
    else:
        print("\n结论: 两个 API 都无法获取数据，可能是非交易时间或网络问题")

if __name__ == "__main__":
    main()
