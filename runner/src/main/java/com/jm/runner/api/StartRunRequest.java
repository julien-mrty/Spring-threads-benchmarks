package com.jm.runner.api;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StartRunRequest {
    public String script;              // e.g. "constant_rate.js"
    public Map<String,String> params;  // e.g. RPS, DURATION, etc.
}
