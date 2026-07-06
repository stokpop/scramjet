package nl.stokpop.scramjet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "management.otlp.metrics.export.enabled=false")
@AutoConfigureMockMvc
class ScramjetApiTests {

    @Autowired
    MockMvcTester mockMvc;

    @Test
    void delayReturnsBurnerMessage() {
        assertThat(mockMvc.get().uri("/delay?duration=1"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().isEqualTo("This was a delay of 1"))
                .hasPathSatisfying("$.name", n -> n.assertThat().isEqualTo("scramjet"))
                .hasPathSatisfying("$.durationInMillis", d -> d.assertThat().isNotNull());
    }

    @Test
    void cpuMagicIdentityCheck() {
        assertThat(mockMvc.get().uri("/cpu/magic-identity-check?matrixSize=5"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().asString().contains("is equal"));
    }

    @Test
    void memoryGrowAndClear() {
        assertThat(mockMvc.get().uri("/memory/grow?objects=2&items=2&length=10")).hasStatusOk();
        assertThat(mockMvc.get().uri("/memory/clear"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().isEqualTo("There are now 0 objects, containing a total of 0 items."));
    }

    @Test
    void directMemoryGrowAndClear() {
        assertThat(mockMvc.get().uri("/memory/direct/grow?buffers=2&size=8192")).hasStatusOk();
        assertThat(mockMvc.get().uri("/memory/direct/clear"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().isEqualTo("There are now 0 direct ByteBuffers, holding a total of 0 MB off-heap."));
    }

    @Test
    void segmentMemoryGrowAndClear() {
        assertThat(mockMvc.get().uri("/memory/segment/grow?segments=2&size=8192")).hasStatusOk();
        assertThat(mockMvc.get().uri("/memory/segment/clear"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().isEqualTo("There are now 0 memory segments, holding a total of 0 MB native memory."));
    }

    @Test
    void flakyNeverFailsWithZeroFlakiness() {
        assertThat(mockMvc.get().uri("/flaky?flakiness=0&maxRandomDelay=1")).hasStatusOk();
    }

    @Test
    void flakyAlwaysFailsWithFullFlakiness() {
        assertThat(mockMvc.get().uri("/flaky?flakiness=101&maxRandomDelay=1"))
                .hasStatus(500)
                .bodyJson()
                .hasPathSatisfying("$.errorCode", e -> e.assertThat().isEqualTo(500));
    }

    @Test
    void parallelInfoShowsCommonPool() {
        assertThat(mockMvc.get().uri("/parallel-info"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.name", n -> n.assertThat().isEqualTo("ForkJoinPool.commonPool()"));
    }

    @Test
    void systemInfoShowsThreads() {
        assertThat(mockMvc.get().uri("/system-info"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.availableProcessors", p -> p.assertThat().isNotNull());
    }

    @Test
    void oneLockSurvives() {
        assertThat(mockMvc.get().uri("/one-lock?duration=1"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().isEqualTo("Survived lock wait"));
    }

    @Test
    void logSomeLogsLines() {
        assertThat(mockMvc.get().uri("/log-some?logLines=2&logSize=20")).hasStatusOk();
    }

    @Test
    void mindMyBusinessSleeps() {
        assertThat(mockMvc.get().uri("/mind-my-business?duration=1")).hasStatusOk();
    }

    @Test
    void openApiDocsAvailable() {
        assertThat(mockMvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.paths./delay.get.summary", s -> s.assertThat().asString().contains("simple java sleep"));
    }

    @Test
    void serialStreamSumsPrimes() {
        assertThat(mockMvc.get().uri("/serial-stream?primeDelayMillis=0&maxPrime=100"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.message", m -> m.assertThat().isEqualTo("The sum of prime numbers up to 100: 1060"));
    }
}
