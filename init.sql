-- Initialize database
CREATE DATABASE IF NOT EXISTS stock_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE stock_platform;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT 'Username',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'Email',
    password VARCHAR(255) NOT NULL COMMENT 'Password',
    phone VARCHAR(20) COMMENT 'Phone',
    avatar VARCHAR(255) COMMENT 'Avatar URL',
    status TINYINT DEFAULT 1 COMMENT 'Status: 0-disabled, 1-enabled',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Users table';

-- Stocks table
CREATE TABLE IF NOT EXISTS stocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE COMMENT 'Stock symbol',
    name VARCHAR(100) NOT NULL COMMENT 'Stock name',
    exchange VARCHAR(20) COMMENT 'Exchange',
    industry VARCHAR(50) COMMENT 'Industry',
    market_cap DECIMAL(20, 2) COMMENT 'Market cap',
    status TINYINT DEFAULT 1 COMMENT 'Status: 0-delisted, 1-active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_symbol (symbol),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stocks table';

-- User favorites table
CREATE TABLE IF NOT EXISTS user_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'User ID',
    stock_id BIGINT NOT NULL COMMENT 'Stock ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_stock (user_id, stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User favorites table';

-- Stock daily data table
CREATE TABLE IF NOT EXISTS stock_daily_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id BIGINT NOT NULL COMMENT 'Stock ID',
    trade_date DATE NOT NULL COMMENT 'Trade date',
    open_price DECIMAL(10, 2) NOT NULL COMMENT 'Open price',
    high_price DECIMAL(10, 2) NOT NULL COMMENT 'High price',
    low_price DECIMAL(10, 2) NOT NULL COMMENT 'Low price',
    close_price DECIMAL(10, 2) NOT NULL COMMENT 'Close price',
    volume BIGINT COMMENT 'Volume',
    amount DECIMAL(20, 2) COMMENT 'Amount',
    change_percent DECIMAL(5, 2) COMMENT 'Change percent',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    UNIQUE KEY uk_stock_date (stock_id, trade_date),
    INDEX idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stock daily data table';

-- Stock realtime data table
CREATE TABLE IF NOT EXISTS stock_realtime_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id BIGINT NOT NULL COMMENT 'Stock ID',
    current_price DECIMAL(10, 2) NOT NULL COMMENT 'Current price',
    change_price DECIMAL(10, 2) COMMENT 'Change price',
    change_percent DECIMAL(5, 2) COMMENT 'Change percent',
    volume BIGINT COMMENT 'Volume',
    amount DECIMAL(20, 2) COMMENT 'Amount',
    bid_price1 DECIMAL(10, 2) COMMENT 'Bid price 1',
    bid_volume1 BIGINT COMMENT 'Bid volume 1',
    ask_price1 DECIMAL(10, 2) COMMENT 'Ask price 1',
    ask_volume1 BIGINT COMMENT 'Ask volume 1',
    high_price DECIMAL(10, 2) COMMENT 'High price',
    low_price DECIMAL(10, 2) COMMENT 'Low price',
    open_price DECIMAL(10, 2) COMMENT 'Open price',
    pre_close DECIMAL(10, 2) COMMENT 'Previous close',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    UNIQUE KEY uk_stock_realtime (stock_id),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stock realtime data table';

-- Insert sample stocks
INSERT INTO stocks (symbol, name, exchange, industry) VALUES
('000001', 'Ping An Bank', 'SZ', 'Bank'),
('000002', 'Vanke A', 'SZ', 'Real Estate'),
('000858', 'Wuliangye', 'SZ', 'Liquor'),
('002415', 'Hikvision', 'SZ', 'Electronics'),
('002594', 'BYD', 'SZ', 'Automobile'),
('300750', 'CATL', 'SZ', 'New Energy'),
('600000', 'SPD Bank', 'SH', 'Bank'),
('600036', 'China Merchants Bank', 'SH', 'Bank'),
('600276', 'Hengrui Medicine', 'SH', 'Pharmaceutical'),
('600519', 'Kweichow Moutai', 'SH', 'Liquor'),
('600887', 'Yili Group', 'SH', 'Food'),
('601318', 'Ping An Insurance', 'SH', 'Insurance'),
('601398', 'ICBC', 'SH', 'Bank'),
('601857', 'PetroChina', 'SH', 'Petroleum'),
('603288', 'Haitian Flavouring', 'SH', 'Food');
