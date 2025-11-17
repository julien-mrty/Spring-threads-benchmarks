package com.jm.runner.api;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class StartRunRequest {
    public String script;              // e.g. "constant_rate.js"
    public Map<String, String> params;  // e.g. RPS, DURATION, etc.
    public Long startedAt;
    public Long durationSec;
}
