package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.domain.SystemInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
public class SystemInfoController {

    /**
     * Show system info for this jvm: memory, cpu and threads.
     */
    @Operation(summary = "Show system info for this jvm, memory, cpu and threads.")
    @GetMapping("/system-info")
    public SystemInfo systemInfo() {
        Runtime runtime = Runtime.getRuntime();

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        List<String> threadNames = threadSet.stream()
                .map(t -> t.getName() + "-" + t.getState())
                .sorted()
                .toList();

        return new SystemInfo(
                runtime.availableProcessors(),
                runtime.maxMemory(),
                runtime.freeMemory(),
                runtime.totalMemory(),
                threadSet.size(),
                threadNames);
    }
}
