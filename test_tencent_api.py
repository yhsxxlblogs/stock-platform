#!/usr/bin/env python3
"""
测试腾讯API返回的大盘指数数据格式
"""
import requests
import json

# 腾讯API地址
TENCENT_API = "https://qt.gtimg.cn/q="

# 大盘指数代码（腾讯格式）
indices = {
    "上证指数": "sh000001",
    "深证成指": "sz399001",
    "创业板指": "sz399006",
    "科创50": "sh000688"
}

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://qt.gtimg.cn"
}

def parse_tencent_data(data):
    """解析腾讯API返回的数据"""
    results = {}

    lines = data.split(";")
    for line in lines:
        line = line.strip()
        if not line:
            continue

        # 提取引号内的内容
        start = line.find('"')
        end = line.rfind('"')
        if start == -1 or end == -1 or start >= end:
            continue

        content = line[start + 1:end]
        fields = content.split("~")

        if len(fields) < 35:
            continue

        # 打印所有字段索引和内容（前40个）
        print(f"\n{'='*60}")
        print(f"指数: {fields[1]} (代码: {fields[2]})")
        print(f"{'='*60}")

        # 关键字段
        key_fields = {
            0: "未知",
            1: "指数名称",
            2: "指数代码",
            3: "当前价格",
            4: "昨收",
            5: "今开",
            32: "涨跌额(大盘)",
            33: "涨跌幅%(大盘)",
        }

        print("\n关键字段:")
        for idx, desc in key_fields.items():
            if idx < len(fields):
                print(f"  fields[{idx}] ({desc}): {fields[idx]}")

        # 计算验证
        current_price = float(fields[3]) if fields[3] else 0
        pre_close = float(fields[4]) if fields[4] else 0
        change_price_from_api = float(fields[32]) if len(fields) > 32 and fields[32] else 0
        change_percent_from_api = float(fields[33]) if len(fields) > 33 and fields[33] else 0

        # 自己计算
        calculated_change = current_price - pre_close
        calculated_percent = (calculated_change / pre_close * 100) if pre_close else 0

        print(f"\n数据验证:")
        print(f"  当前价格: {current_price}")
        print(f"  昨收: {pre_close}")
        print(f"  API返回涨跌额: {change_price_from_api}")
        print(f"  API返回涨跌幅: {change_percent_from_api}%")
        print(f"  计算涨跌额: {calculated_change:.2f}")
        print(f"  计算涨跌幅: {calculated_percent:.2f}%")

        results[fields[2]] = {
            "name": fields[1],
            "current_price": current_price,
            "pre_close": pre_close,
            "api_change_price": change_price_from_api,
            "api_change_percent": change_percent_from_api,
            "calculated_change": calculated_change,
            "calculated_percent": calculated_percent
        }

    return results

def main():
    # 构建URL
    symbols = ",".join(indices.values())
    url = TENCENT_API + symbols

    print(f"请求URL: {url}")
    print(f"\n请求指数: {list(indices.keys())}")

    try:
        response = requests.get(url, headers=headers, timeout=10)
        response.encoding = 'gbk'  # 腾讯API返回GBK编码

        print(f"\n状态码: {response.status_code}")
        print(f"原始数据长度: {len(response.text)}")
        print(f"\n原始数据:\n{response.text[:500]}...")

        # 解析数据
        results = parse_tencent_data(response.text)

        print(f"\n{'='*60}")
        print("总结:")
        print(f"{'='*60}")
        for code, data in results.items():
            print(f"\n{data['name']} ({code}):")
            print(f"  当前: {data['current_price']:.2f}")
            print(f"  昨收: {data['pre_close']:.2f}")
            print(f"  API涨跌额: {data['api_change_price']:.2f}")
            print(f"  API涨跌幅: {data['api_change_percent']:.2f}%")
            print(f"  计算涨跌幅: {data['calculated_percent']:.2f}%")

    except Exception as e:
        print(f"请求失败: {e}")

if __name__ == "__main__":
    main()
