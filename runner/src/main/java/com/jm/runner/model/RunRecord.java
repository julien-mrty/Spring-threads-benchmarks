package com.jm.runner.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class RunRecord {
    public String id;
    public String script;
    public Map<String, String> params;
    public Long startedAt;
    public Long durationSec;
    public RunStatus status;
    public String summaryPath;
}
