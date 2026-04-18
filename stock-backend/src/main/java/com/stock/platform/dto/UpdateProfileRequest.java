package com.stock.platform.dto;

import lombok.Data;

/**
 * 更新个人信息请求
 */
@Data
public class UpdateProfileRequest {
    private String email;
    private String phone;
    private String avatar;
}
