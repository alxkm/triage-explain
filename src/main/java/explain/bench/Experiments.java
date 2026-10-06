package explain.bench;

import callguard.Policy;
import callguard.bench.MockProvider;
import explain.Explanation;
import explain.Incident;
import explain.ModelAttributor;
import explain.ModelExplainer;
import explain.port.ChatPort;
import explain.port.LangChain4jChatPort;
import explain.port.SpringAiChatPort;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import triage.core.Cause;

/**
 * The experiments of article 2.b. Results are appended row by row, so an interrupted run resumes where it stopped.
 *
 * <pre>
 * java -cp &lt;classpath&gt; explain.bench.Experiments X1 spring-ai:qwen2.5:7b langchain4j:qwen2.5:7b ...   explanations
 * java -cp &lt;classpath&gt; explain.bench.Experiments X2 spring-ai:qwen2.5:7b ...                          the model as attributor
 * java -cp &lt;classpath&gt; explain.bench.Experiments X3                                                  provider failures
 * </pre>
 *
 * The model server is {@code OLLAMA_BASE} (default {@code http://localhost:11434/v1}).
 */
public final class Experiments {
    static final String BASE = System.getenv().getOrDefault("OLLAMA_BASE", "http://localhost:11434/v1");
    /** {@code incidents} (test) or {@code incidents_dev}; output files carry the same suffix. */
    static final String INCIDENTS = System.getenv().getOrDefault("INCIDENTS", "incidents");
    static final String SUFFIX = INCIDENTS.equals("incidents") ? "" : INCIDENTS.substring("incidents".length());
    /** Generation on a local GPU can take tens of seconds for a 32B model; the budget allows two attempts. */
    static final Policy POLICY = Policy.defaults().withTimeouts(Duration.ofSeconds(120), Duration.ofSeconds(60), Duration.ofSeconds(30), Duration.ofSeconds(300));

    public static void main(String[] args) throws Exception {
        List<Incident> incidents = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("results/" + INCIDENTS + ".jsonl"), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                incidents.add(IncidentJson.read(line));
            }
        }
        String which = args[0];
        List<String> ports = List.of(args).subList(1, args.length);
        Files.writeString(Path.of("results/environment_" + which + SUFFIX + ".txt"), "run: " + ZonedDateTime.now() + "\nbase: " + BASE + "\nports: " + ports
                + "\npolicy: " + POLICY + "\nincidents: " + incidents.size() + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        switch (which) {
            case "X1" -> {
                for (String p : ports) {
                    explanations(port(p, BASE), incidents, Path.of("results/X1_explanations" + SUFFIX + ".csv"));
                }
            }
            case "X2" -> {
                for (String p : ports) {
                    attribution(port(p, BASE), incidents, Path.of("results/X2_attribution" + SUFFIX + ".csv"));
                }
            }
            case "X3" -> failures(incidents.subList(0, Math.min(20, incidents.size())));
            default -> throw new IllegalArgumentException(which);
        }
        System.exit(0);
    }

    static ChatPort port(String spec, String base) {
        int c = spec.indexOf(':');
        String fw = spec.substring(0, c);
        String model = spec.substring(c + 1);
        return switch (fw) {
            case "spring-ai" -> new SpringAiChatPort(base, model, POLICY, ObservationRegistry.NOOP);
            case "langchain4j" -> new LangChain4jChatPort(base, model, POLICY, ObservationRegistry.NOOP);
            default -> throw new IllegalArgumentException(spec);
        };
    }

    static void explanations(ChatPort port, List<Incident> incidents, Path out) throws IOException {
        Set<String> done = done(out, "port,id,cause,truth,fault,source,rejected,millis,words,final_text,model_reply\n", 0, 1);
        ModelExplainer ex = new ModelExplainer(port, ObservationRegistry.NOOP);
        for (Incident i : incidents) {
            if (done.contains(port.name() + "|" + i.id())) {
                continue;
            }
            Explanation e = ex.explain(i);
            String row = String.join(",", port.name(), i.id(), i.cause().name(), i.truth().name(), i.fault(), e.source(), q(String.join(" ", e.rejected())),
                    Long.toString(e.millis()), Integer.toString(e.text().strip().split("\\s+").length), q(e.text()), q(e.raw())) + "\n";
            Files.writeString(out, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            System.out.printf("%s %s %s %s %d ms %s%n", port.name(), i.id(), i.cause(), e.source(), e.millis(), e.rejected());
        }
    }

    static void attribution(ChatPort port, List<Incident> incidents, Path out) throws IOException {
        Set<String> done = done(out, "port,input,id,truth,fault,decided,model_cause,millis\n", 0, 2, 1);
        ModelAttributor at = new ModelAttributor(port);
        for (ModelAttributor.Input in : ModelAttributor.Input.values()) {
            for (Incident i : incidents) {
                if (done.contains(port.name() + "|" + i.id() + "|" + in.name())) {
                    continue;
                }
                long t0 = System.nanoTime();
                Cause c = at.attribute(i, in);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                String row = String.join(",", port.name(), in.name(), i.id(), i.truth().name(), i.fault(), i.cause().name(), c == null ? "NONE" : c.name(),
                        Long.toString(ms)) + "\n";
                Files.writeString(out, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                System.out.printf("%s %s %s truth=%s decided=%s model=%s %d ms%n", port.name(), in, i.id(), i.truth(), i.cause(), c, ms);
            }
        }
    }

    /** The explainer when the provider fails: every explanation must fall back to the template within the policy's budget. */
    static void failures(List<Incident> incidents) throws IOException {
        Policy p = Policy.defaults().withTimeouts(Duration.ofSeconds(3), Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(60));
        Path out = Path.of("results/X3_failures.csv");
        Files.writeString(out, "port,scenario,id,source,rejected,millis,attempts\n", StandardCharsets.UTF_8);
        Map<String, MockProvider.Behaviour> scenarios = new java.util.LinkedHashMap<>();
        scenarios.put("503 on every attempt", new MockProvider.Status(503, Map.of()));
        scenarios.put("429 Retry-After 60 s", new MockProvider.Status(429, Map.of("Retry-After", "60")));
        scenarios.put("no response for 70 s", new MockProvider.Hang(70_000));
        scenarios.put("200 with a body that is not JSON", new MockProvider.Malformed());
        try (MockProvider mock = new MockProvider()) {
            for (String fw : List.of("spring-ai", "langchain4j")) {
                ChatPort port = fw.equals("spring-ai") ? new SpringAiChatPort(mock.baseUrl(), "test-model", p, ObservationRegistry.NOOP)
                        : new LangChain4jChatPort(mock.baseUrl(), "test-model", p, ObservationRegistry.NOOP);
                ModelExplainer ex = new ModelExplainer(port, ObservationRegistry.NOOP);
                for (Map.Entry<String, MockProvider.Behaviour> s : scenarios.entrySet()) {
                    for (Incident i : incidents.subList(0, 5)) {
                        mock.script(s.getValue());
                        Explanation e = ex.explain(i);
                        String row = String.join(",", port.name(), q(s.getKey()), i.id(), e.source(), q(String.join(" ", e.rejected())),
                                Long.toString(e.millis()), Integer.toString(mock.attempts().size())) + "\n";
                        Files.writeString(out, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                        System.out.print(row);
                    }
                }
            }
        }
    }

    private static Set<String> done(Path out, String header, int... keyColumns) throws IOException {
        Set<String> done = new HashSet<>();
        if (!Files.exists(out)) {
            Files.writeString(out, header, StandardCharsets.UTF_8);
            return done;
        }
        for (String line : Files.readAllLines(out, StandardCharsets.UTF_8).subList(1, Files.readAllLines(out).size())) {
            String[] f = line.split(",", -1);
            StringBuilder k = new StringBuilder();
            for (int c : keyColumns) {
                k.append(k.isEmpty() ? "" : "|").append(f[c]);
            }
            done.add(k.toString());
        }
        return done;
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "'").replace("\r", " ").replace("\n", " ")) + "\"";
    }
}
