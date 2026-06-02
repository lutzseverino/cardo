package com.odonta.common.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PageRequests {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_PAGE_SIZE = 50;
  public static final int MAX_PAGE_SIZE = 500;

  private PageRequests() {}

  public static Pageable oneBased(Integer page, Integer pageSize) {
    return oneBased(
        valueOrDefault(page, DEFAULT_PAGE), valueOrDefault(pageSize, DEFAULT_PAGE_SIZE));
  }

  public static Pageable oneBased(int page, int pageSize) {
    int normalizedPage = Math.max(page, DEFAULT_PAGE);
    int normalizedPageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    return PageRequest.of(normalizedPage - 1, normalizedPageSize);
  }

  private static int valueOrDefault(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
  }
}
