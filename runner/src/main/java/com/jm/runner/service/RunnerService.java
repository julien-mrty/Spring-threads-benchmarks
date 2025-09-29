package com.jm.runner.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback.Adapter;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import com.jm.runner.api.StartRunRequest;
import com.jm.runner.config.RunnerProperties;
import com.jm.runner.model.RunRecord;
import com.jm.runner.model.RunStatus;

@Service
public class RunnerService {

    private final DockerClient docker;
    private final RunnerProperties props;
    private final Counter started, succeeded, failed;
    private volatile int active = 0;
    private final Map<String, RunRecord> runs = new ConcurrentHashMap<>();
    private final ExecutorService execPool;

    private static final Pattern SAFE_SCRIPT =
            Pattern.compile("^[a-zA-Z0-9._\\-\\/]+\\.js$");

    public RunnerService(DockerClient docker, RunnerProperties props, MeterRegistry mr) {
        this.docker = docker;
        this.props = props;
        this.started = mr.counter("k6_runs_started_total");
        this.succeeded = mr.counter("k6_runs_succeeded_total");
        this.failed = mr.counter("k6_runs_failed_total");
        Gauge.builder("k6_runs_active", () -> active).register(mr);
        this.execPool = Executors.newFixedThreadPool(Math.max(1, props.getMaxConcurrency()));
    }

    public Collection<RunRecord> list() {
        return runs.values().stream()
                .sorted(Comparator.comparing((RunRecord r) -> r.start).reversed())
                .toList();
    }

    public RunRecord get(String id) { return runs.get(id); }

    public RunRecord enqueue(StartRunRequest req) {
        Objects.requireNonNull(req.script, "script is required");
        if (!SAFE_SCRIPT.matcher(req.script).matches() || req.script.contains(".."))
            throw new IllegalArgumentException("invalid script name");

        Path script = Path.of(props.getScriptsDir(), req.script).normalize();

        if (!Files.exists(script))
            throw new IllegalArgumentException("script not found: " + req.script);

        Map<String,String> params = Optional.ofNullable(req.params).orElseGet(HashMap::new);

        // Enforce BASE_URL allow-list
        String baseUrl = params.getOrDefault("BASE_URL", props.getAllowBaseUrl());
        if (!baseUrl.equals(props.getAllowBaseUrl()))
            throw new IllegalArgumentException("BASE_URL must be " + props.getAllowBaseUrl());

        params.put("BASE_URL", baseUrl);

        String id = UUID.randomUUID().toString().substring(0,10);
        String summaryPath = props.getResultsDir() + "/" + id + ".json";

        RunRecord rec = new RunRecord();
        rec.id = id;
        rec.script = req.script;
        rec.params = params;
        rec.start = Instant.now();
        rec.status = RunStatus.QUEUED;
        rec.summaryPath = summaryPath;
        runs.put(id, rec);

        execPool.submit(() -> runOne(rec));

        return rec;
    }

    private void runOne(RunRecord rec) {
        rec.status = RunStatus.RUNNING;
        started.increment();
        active++;
        try {
            // Build env list for docker exec
            List<String> envList = new ArrayList<>();
            envList.add("K6_PROMETHEUS_RW_SERVER_URL=" + props.getPromRemoteWriteUrl());
            envList.add("K6_COMPATIBILITY_MODE=extended");
            for (var e : rec.params.entrySet()) {
                envList.add(e.getKey() + "=" + String.valueOf(e.getValue()));
            }

            String scriptPath = props.getScriptsDir() + "/" + rec.script;

            ExecCreateCmdResponse execCreate = docker.execCreateCmd(props.getK6Container())
                    .withAttachStdout(true).withAttachStderr(true)
                    .withEnv(envList)
                    .withCmd(
                            "k6","run",
                            "--compatibility-mode=extended",
                            "-o","experimental-prometheus-rw",
                            "--summary-export", rec.summaryPath,
                            scriptPath
                    )
                    .exec();

            var latch = new CountDownLatch(1);
            docker.execStartCmd(execCreate.getId())
                    .exec(new Adapter<Frame>() {
                        @Override public void onNext(Frame frame) {
                            System.out.print("[k6 " + rec.id + "] " + new String(frame.getPayload()));
                        }
                        @Override public void onComplete() { latch.countDown(); }
                        @Override public void onError(Throwable t) { t.printStackTrace(); latch.countDown(); }
                    });
            latch.await();

            var execId = execCreate.getId();
            var inspect = docker.inspectExecCmd(execId).exec();
            Integer code = inspect.getExitCode();

            rec.status = (code != null && code == 0) ? RunStatus.SUCCEEDED : RunStatus.FAILED;
            if (rec.status == RunStatus.SUCCEEDED) succeeded.increment(); else failed.increment();

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            rec.status = RunStatus.FAILED; failed.increment();
        } catch (Exception e) {
            e.printStackTrace();
            rec.status = RunStatus.FAILED; failed.increment();
        } finally {
            rec.end = Instant.now();
            active--;
        }
    }
}
