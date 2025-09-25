package com.jm.runner.model;

import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RunRecord {
    public String id;
    public String script;
    public Map<String,String> params;
    public Instant start;
    public Instant end;
    public RunStatus status;
    public String summaryPath;
}
