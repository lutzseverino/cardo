package com.odonta.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageRequestsTest {

  @Test
  void convertsOneBasedRequestsToSpringPageables() {
    var pageable = PageRequests.oneBased(2, 25);

    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(25);
  }

  @Test
  void defaultsMissingAndInvalidValues() {
    var pageable = PageRequests.oneBased(null, 0);

    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(PageRequests.DEFAULT_PAGE_SIZE);
  }

  @Test
  void clampsOversizedPageRequests() {
    var pageable = PageRequests.oneBased(0, 600);

    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(PageRequests.MAX_PAGE_SIZE);
  }
}
