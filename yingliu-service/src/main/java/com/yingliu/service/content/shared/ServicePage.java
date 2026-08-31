package com.yingliu.service.content.shared;

import java.util.List;

public record ServicePage<T>(List<T> items, long total) {
}
