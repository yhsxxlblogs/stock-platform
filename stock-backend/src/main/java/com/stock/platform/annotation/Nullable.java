package com.stock.platform.annotation;

import java.lang.annotation.*;

/**
 * 标记参数、返回值或字段可以为 null
 * 用于消除 IDE 的空安全警告
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
public @interface Nullable {
}
