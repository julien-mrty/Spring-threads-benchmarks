package com.jm.runner.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.jm.runner.service.RunnerService;
import com.jm.runner.config.RunnerProperties;
import com.jm.runner.model.RunRecord;
import com.jm.runner.api.StartRunRequest;
import java.io.File;
import java.util.Collection;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/runs")
public class RunsController {

    private final RunnerService service;
    private final RunnerProperties props;

    public RunsController(RunnerService s, RunnerProperties p) {
        this.service = s;
        this.props = p;
    }

    @GetMapping
    public Collection<RunRecord> list() { return service.list(); }

    @GetMapping("/{id}")
    public ResponseEntity<RunRecord> get(@PathVariable String id) {
        var r = service.get(id);
            if (r == null)
                throw new ResponseStatusException(NOT_FOUND, "Run not found: " + id);
        return ResponseEntity.ok(r);
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<FileSystemResource> summary(@PathVariable String id) {
        var r = service.get(id);

        if (r == null)
            throw new ResponseStatusException(NOT_FOUND, "Run not found: " + id);

        if (r.summaryPath == null)
            throw new ResponseStatusException(NOT_FOUND, "Summary not available yet");

        File f = new File(r.summaryPath);
        if (!f.exists())
            throw new ResponseStatusException(NOT_FOUND, "Summary file missing");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FileSystemResource(f));
    }

    @PostMapping
    public ResponseEntity<RunRecord> start(@RequestBody StartRunRequest req) {
        var r = service.enqueue(req);
        return ResponseEntity.accepted().body(r);
    }
}
