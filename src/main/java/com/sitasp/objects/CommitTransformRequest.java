package com.sitasp.objects;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(fluent = true)
public class CommitTransformRequest {
    String repoPath;
    LocalDateTime startDate;
    LocalDateTime endDate;
}
