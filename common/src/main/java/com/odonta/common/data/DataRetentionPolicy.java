package com.odonta.common.data;

import java.time.OffsetDateTime;

public interface DataRetentionPolicy {

  OffsetDateTime retainUntil(OffsetDateTime archivedAt);

  DataRetentionReason reason();
}
