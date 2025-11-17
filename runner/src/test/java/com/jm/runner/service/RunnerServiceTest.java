package com.jm.runner.service;

import com.github.dockerjava.api.DockerClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jm.runner.api.StartRunRequest;
import com.jm.runner.config.RunnerProperties;
import com.jm.runner.model.RunRecord;
import com.jm.runner.model.RunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RunnerServiceTest {

    @TempDir
    Path tmp;

    private RunnerProperties props() {
        return new RunnerProperties("k6",
                "http://backend:8080",
                "http://prometheus:9090/api/v1/write",
                tmp.resolve("work").toString(),
                tmp.resolve("runs").toString()
        );
    }

    @Test
    void enqueue_rejects_nonexistent_script() throws Exception {
        var mr = new SimpleMeterRegistry();
        var docker = mock(DockerClient.class);
        Files.createDirectories(tmp.resolve("work"));
        Files.createDirectories(tmp.resolve("runs"));

        var service = new RunnerService(docker, props(), mr);

        var req = new StartRunRequest();
        req.script = "missing.js";
        req.params = Map.of("BASE_URL", "http://backend:8080");

        assertThatThrownBy(() -> service.enqueue(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("script not found");
    }

    @Test
    void enqueue_rejects_wrong_base_url() throws Exception {
        var mr = new SimpleMeterRegistry();
        var docker = mock(DockerClient.class);
        Files.createDirectories(tmp.resolve("work"));
        Files.createDirectories(tmp.resolve("runs"));
        Files.writeString(tmp.resolve("work/ok.js"), "export default function(){}");

        var service = new RunnerService(docker, props(), mr);

        var req = new StartRunRequest();
        req.script = "ok.js";
        req.params = Map.of("BASE_URL", "http://google.com");

        assertThatThrownBy(() -> service.enqueue(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BASE_URL must be");
    }

    @Test
    void run_succeeds_and_updates_status_and_metrics() throws Exception {
        var mr = new SimpleMeterRegistry();
        var docker = mockDockerSuccess();
        Files.createDirectories(tmp.resolve("work"));
        Files.createDirectories(tmp.resolve("runs"));
        Files.writeString(tmp.resolve("work/ok.js"), "export default function(){}");

        var service = new RunnerService(docker, props(), mr);

        var req = new StartRunRequest();
        req.script = "ok.js";
        req.params = new HashMap<>(Map.of("BASE_URL", "http://backend:8080"));
        req.startedAt = 1234L;
        req.durationSec = 60L;

        RunRecord rec = service.enqueue(req);

        // Wait briefly for async task to complete
        Thread.sleep(200); // unit-test cheap wait; use Awaitility if you prefer
        assertThat(rec.status).isIn(RunStatus.SUCCEEDED, RunStatus.FAILED);
        assertThat(rec.startedAt).isNotNull();
        assertThat(rec.durationSec).isNotNull();
        assertThat(mr.counter("k6_runs_started_total").count()).isEqualTo(1.0);
    }

    // ----- helper -----
    private DockerClient mockDockerSuccess() {
        var docker = mock(DockerClient.class);

        // ---- mock execCreateCmd(...).exec() -> ExecCreateCmdResponse
        var createCmd = mock(com.github.dockerjava.api.command.ExecCreateCmd.class);
        var createResp = mock(com.github.dockerjava.api.command.ExecCreateCmdResponse.class);
        when(createResp.getId()).thenReturn("exec-123");

        when(docker.execCreateCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withAttachStdout(true)).thenReturn(createCmd);
        when(createCmd.withAttachStderr(true)).thenReturn(createCmd);
        when(createCmd.withEnv(anyList())).thenReturn(createCmd);
        when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResp);

        // ---- mock execStartCmd(id).exec(callback) -> call onComplete
        var startCmd = mock(com.github.dockerjava.api.command.ExecStartCmd.class);
        when(docker.execStartCmd("exec-123")).thenReturn(startCmd);
        when(startCmd.exec(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var cb = (com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>) inv.getArgument(0);
            cb.onComplete();
            return cb;
        });

        // ---- mock inspectExecCmd(id).exec() -> InspectExecResponse with exit code
        var inspectCmd = mock(com.github.dockerjava.api.command.InspectExecCmd.class);
        var inspectResp = mock(com.github.dockerjava.api.command.InspectExecResponse.class);
        when(inspectResp.getExitCode()).thenReturn(0);            // <-- stub the getter
        when(docker.inspectExecCmd("exec-123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResp);

        return docker;
    }
}
