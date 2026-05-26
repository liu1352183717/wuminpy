package com.wumin.wuminpy.ui;

// IOnBackPressed.java
public interface IOnBackPressed {
    /**
     * 处理返回键
     * @return true 表示消费了事件，不再继续传递；false 表示未消费，由Activity处理
     */
    boolean onBackPressed();
}