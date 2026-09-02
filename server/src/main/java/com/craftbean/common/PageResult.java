package com.craftbean.common;

import java.util.List;

public record PageResult<T>(List<T> list, long total) {
}
