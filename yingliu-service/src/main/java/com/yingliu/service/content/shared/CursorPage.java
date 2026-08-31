package com.yingliu.service.content.shared;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor) {
}
