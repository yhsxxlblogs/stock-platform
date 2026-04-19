#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
A股全量股票数据爬取脚本
从腾讯财经API获取所有A股（5000+只）股票数据
"""

import requests
import json
import time
from typing import List, Dict

# 腾讯财经API
TENCENT_API = "http://qt.gtimg.cn/q"

# 所有市场配置
MARKETS = [
    {"prefix": "sh", "exchange": "SH", "market_type": "主板", "name": "上海主板", "codes": []},
    {"prefix": "sh", "exchange": "SH", "market_type": "科创板", "name": "上海科创板", "codes": []},
    {"prefix": "sz", "exchange": "SZ", "market_type": "主板", "name": "深圳主板", "codes": []},
    {"prefix": "sz", "exchange": "SZ", "market_type": "创业板", "name": "深圳创业板", "codes": []},
    {"prefix": "bj", "exchange": "BJ", "market_type": "北交所", "name": "北交所", "codes": []},
]

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "*/*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": "http://stockpage.10jqka.com.cn/",
}


def generate_stock_codes():
    """
    生成所有可能的A股代码
    """
    codes = {
        "sh_main": [],  # 上海主板: 600-609, 601-609, 603-609, 605, 688(科创)
        "sh_kcb": [],   # 科创板: 688, 689
        "sz_main": [],  # 深圳主板: 000-009, 001-004
        "sz_cyb": [],   # 创业板: 300-309
        "bj": [],       # 北交所: 430, 831-835, 870-873
    }
    
    # 上海主板 (600-609)
    for i in range(600000, 610000):
        codes["sh_main"].append(f"sh{i}")
    
    # 科创板 (688xxx)
    for i in range(688000, 689000):
        codes["sh_kcb"].append(f"sh{i}")
    
    # 深圳主板 (000xxx)
    for i in range(1, 10000):
        codes["sz_main"].append(f"sz{i:06d}")
    
    # 创业板 (300xxx)
    for i in range(300000, 310000):
        codes["sz_cyb"].append(f"sz{i}")
    
    # 北交所 (8xxxxx)
    for i in range(830000, 836000):
        codes["bj"].append(f"bj{i}")
    for i in range(870000, 874000):
        codes["bj"].append(f"bj{i}")
    for i in range(430000, 440000):
        codes["bj"].append(f"bj{i}")
    
    return codes


def fetch_stocks_batch(codes: List[str]) -> List[Dict]:
    """
    批量获取股票信息
    """
    stocks = []
    batch_size = 800  # 腾讯API一次最多支持800只
    
    for i in range(0, len(codes), batch_size):
        batch = codes[i:i+batch_size]
        codes_str = ",".join(batch)
        
        try:
            url = f"{TENCENT_API}={codes_str}"
            response = requests.get(url, headers=headers, timeout=30)
            
            if response.status_code != 200:
                print(f"  请求失败: HTTP {response.status_code}")
                continue
            
            # 解析腾讯返回的数据格式
            content = response.text
            lines = content.strip().split(";")
            
            for line in lines:
                if not line or "=" not in line:
                    continue
                
                try:
                    # 格式: v_sh600519="1~贵州茅台~600519..."
                    parts = line.split("=")
                    if len(parts) < 2:
                        continue
                    
                    code_key = parts[0].strip()
                    data = parts[1].strip().strip('"')
                    
                    if not data or data == "null":
                        continue
                    
                    fields = data.split("~")
                    if len(fields) < 3:
                        continue
                    
                    # 解析代码
                    code = code_key.replace("v_", "").replace("sh", "").replace("sz", "").replace("bj", "")
                    
                    # 确定市场
                    exchange = "SH"
                    market_type = "主板"
                    
                    if code_key.startswith("v_sh"):
                        exchange = "SH"
                        if code.startswith("688") or code.startswith("689"):
                            market_type = "科创板"
                        else:
                            market_type = "主板"
                    elif code_key.startswith("v_sz"):
                        exchange = "SZ"
                        if code.startswith("300") or code.startswith("301"):
                            market_type = "创业板"
                        else:
                            market_type = "主板"
                    elif code_key.startswith("v_bj"):
                        exchange = "BJ"
                        market_type = "北交所"
                    
                    stock = {
                        "symbol": code,
                        "name": fields[1] if len(fields) > 1 else "",
                        "exchange": exchange,
                        "market_type": market_type,
                        "industry": fields[2] if len(fields) > 2 else "其他"
                    }
                    
                    if stock["name"] and stock["name"] != "":
                        stocks.append(stock)
                
                except Exception as e:
                    continue
            
            print(f"  批次 {i//batch_size + 1}: 获取 {len(stocks)} 只股票")
            time.sleep(0.5)  # 避免请求过快
            
        except Exception as e:
            print(f"  批次出错: {str(e)}")
            continue
    
    return stocks


def fetch_all_stocks():
    """
    获取所有A股股票数据
    """
    print("=" * 60)
    print("开始爬取A股全市场股票数据 (腾讯API)")
    print("=" * 60)
    
    all_stocks = []
    codes = generate_stock_codes()
    
    # 上海主板
    print("\n开始获取 上海主板 股票数据...")
    sh_main = fetch_stocks_batch(codes["sh_main"])
    print(f"上海主板 完成，共 {len(sh_main)} 只股票")
    all_stocks.extend(sh_main)
    
    # 科创板
    print("\n开始获取 上海科创板 股票数据...")
    sh_kcb = fetch_stocks_batch(codes["sh_kcb"])
    print(f"上海科创板 完成，共 {len(sh_kcb)} 只股票")
    all_stocks.extend(sh_kcb)
    
    # 深圳主板
    print("\n开始获取 深圳主板 股票数据...")
    sz_main = fetch_stocks_batch(codes["sz_main"])
    print(f"深圳主板 完成，共 {len(sz_main)} 只股票")
    all_stocks.extend(sz_main)
    
    # 创业板
    print("\n开始获取 深圳创业板 股票数据...")
    sz_cyb = fetch_stocks_batch(codes["sz_cyb"])
    print(f"深圳创业板 完成，共 {len(sz_cyb)} 只股票")
    all_stocks.extend(sz_cyb)
    
    # 北交所
    print("\n开始获取 北交所 股票数据...")
    bj = fetch_stocks_batch(codes["bj"])
    print(f"北交所 完成，共 {len(bj)} 只股票")
    all_stocks.extend(bj)
    
    print("\n" + "=" * 60)
    print(f"爬取完成！共获取 {len(all_stocks)} 只股票")
    print("=" * 60)
    
    return all_stocks


def save_to_json(stocks: List[Dict], filename: str):
    """
    保存到JSON文件
    """
    # 按市场分组
    markets_data = {
        "SH_MAIN": {"name": "上海主板", "count": 0, "stocks": []},
        "SH_KCB": {"name": "上海科创板", "count": 0, "stocks": []},
        "SZ_MAIN": {"name": "深圳主板", "count": 0, "stocks": []},
        "SZ_CYB": {"name": "深圳创业板", "count": 0, "stocks": []},
        "BJ": {"name": "北交所", "count": 0, "stocks": []},
    }
    
    for stock in stocks:
        exchange = stock["exchange"]
        market_type = stock["market_type"]
        
        if exchange == "SH":
            if market_type == "科创板":
                markets_data["SH_KCB"]["stocks"].append(stock)
            else:
                markets_data["SH_MAIN"]["stocks"].append(stock)
        elif exchange == "SZ":
            if market_type == "创业板":
                markets_data["SZ_CYB"]["stocks"].append(stock)
            else:
                markets_data["SZ_MAIN"]["stocks"].append(stock)
        elif exchange == "BJ":
            markets_data["BJ"]["stocks"].append(stock)
    
    # 更新计数
    for key in markets_data:
        markets_data[key]["count"] = len(markets_data[key]["stocks"])
    
    # 构建最终数据结构
    result = {
        "version": time.strftime("%Y.%m.%d"),
        "totalCount": len(stocks),
        "lastUpdated": time.strftime("%Y-%m-%d %H:%M:%S"),
        "description": "A股全市场股票清单 - 自动爬取自腾讯财经",
        "markets": markets_data
    }
    
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    
    print(f"\n数据已保存到: {filename}")
    print(f"上海主板: {markets_data['SH_MAIN']['count']} 只")
    print(f"上海科创板: {markets_data['SH_KCB']['count']} 只")
    print(f"深圳主板: {markets_data['SZ_MAIN']['count']} 只")
    print(f"深圳创业板: {markets_data['SZ_CYB']['count']} 只")
    print(f"北交所: {markets_data['BJ']['count']} 只")


if __name__ == "__main__":
    stocks = fetch_all_stocks()
    
    if stocks:
        output_file = "../stock-backend/src/main/resources/stocks-data.json"
        save_to_json(stocks, output_file)
        print("\n✅ 股票数据爬取和保存完成！")
    else:
        print("\n❌ 未获取到任何股票数据")
