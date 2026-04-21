#!/usr/bin/env python3
"""
测试腾讯API返回的大盘指数数据格式 - 详细字段分析
"""
import requests

# 腾讯API地址
TENCENT_API = "https://qt.gtimg.cn/q="

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://qt.gtimg.cn"
}

def main():
    url = TENCENT_API + "sh000001"

    print(f"请求URL: {url}\n")

    response = requests.get(url, headers=headers, timeout=10)
    response.encoding = 'gbk'

    data = response.text
    print(f"原始数据:\n{data}\n")

    # 解析数据
    lines = data.split(";")
    for line in lines:
        line = line.strip()
        if not line or "=" not in line:
            continue

        parts = line.split("=")
        if len(parts) < 2:
            continue

        value_str = parts[1].replace('"', "").strip()
        fields = value_str.split("~")

        print(f"总字段数: {len(fields)}\n")

        # 打印所有字段
        print("所有字段索引和内容:")
        print("-" * 60)
        for i, field in enumerate(fields):
            print(f"  fields[{i:2d}]: {field}")

        print("\n" + "=" * 60)
        print("关键字段分析:")
        print("=" * 60)

        # 计算正确的涨跌额和涨跌幅
        current_price = float(fields[3]) if fields[3] else 0
        pre_close = float(fields[4]) if fields[4] else 0

        print(f"\n当前价格 (fields[3]): {current_price}")
        print(f"昨收价格 (fields[4]): {pre_close}")

        # 找到正确的涨跌额和涨跌幅字段
        for i in range(30, min(45, len(fields))):
            try:
                val = float(fields[i])
                # 如果值接近计算的涨跌额
                expected_change = current_price - pre_close
                if abs(val - expected_change) < 0.01:
                    print(f"\n>>> 涨跌额可能在 fields[{i}]: {val} (计算值: {expected_change:.2f})")
                # 如果值接近计算的涨跌幅
                expected_percent = (expected_change / pre_close * 100) if pre_close else 0
                if abs(val - expected_percent) < 0.01:
                    print(f">>> 涨跌幅可能在 fields[{i}]: {val}% (计算值: {expected_percent:.2f}%)")
            except:
                pass

if __name__ == "__main__":
    main()
