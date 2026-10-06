package explain.bench;

import explain.Incident;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import triage.bench.Bench;
import triage.bench.Event;
import triage.bench.FaultType;
import triage.bench.ProcessModel;
import triage.bench.Scenario;
import triage.core.Decision;
import triage.core.TriageChannel;
import triage.core.TriageConfig;

/**
 * Runs the triage method of article 1 on its synthetic test runs (seeds 2000–2049, both process models, level
 * channel only, frozen configuration) and keeps every detected event with the decision matched to it, by the same
 * rule as the benchmark: the first decision whose trigger falls in the event's window. A stratified sample of
 * {@code perType} events per fault type is written to {@code results/incidents.jsonl}.
 *
 * <pre>java -cp &lt;classpath&gt; explain.bench.ExportIncidents [perType] [seedFrom seedTo name]</pre>
 *
 * The defaults are the test seeds and {@code incidents}; development used seeds 1000–1019 ({@code 5 1000 1020 incidents_dev}).
 */
public final class ExportIncidents {
    private record Candidate(Incident incident, FaultType type) {
    }

    public static void main(String[] args) throws IOException {
        int perType = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        long seedFrom = args.length > 2 ? Long.parseLong(args[1]) : Bench.TEST_SEED_FROM;
        long seedTo = args.length > 2 ? Long.parseLong(args[2]) : Bench.TEST_SEED_TO;
        String name = args.length > 3 ? args[3] : "incidents";
        Map<FaultType, List<Incident>> byType = new EnumMap<>(FaultType.class);
        for (FaultType f : FaultType.values()) {
            byType.put(f, new ArrayList<>());
        }
        int decisions = 0;
        for (ProcessModel m : ProcessModel.values()) {
            for (long seed = seedFrom; seed < seedTo; seed++) {
                Scenario s = Scenario.generate(m, seed, Bench.LENGTH, Bench.GAP);
                TriageChannel ch = new TriageChannel(Bench.spec(m), TriageConfig.defaults());
                List<Decision> ds = new ArrayList<>();
                List<List<String>> triggers = new ArrayList<>();
                List<Double> sigmas = new ArrayList<>();
                for (int t = 0; t < s.length(); t++) {
                    Decision d = ch.update(s.y[t]);
                    if (d != null) {
                        ds.add(d);
                        triggers.add(List.copyOf(ch.lastTriggers()));
                        sigmas.add(ch.sigma());
                    }
                }
                decisions += ds.size();
                for (int k = 0; k < s.events.size(); k++) {
                    Event e = s.events.get(k);
                    for (int i = 0; i < ds.size(); i++) {
                        Decision d = ds.get(i);
                        if (d.triggerIndex() >= e.start() && d.triggerIndex() <= e.end() + Bench.TAIL) {
                            int from = (int) Math.max(0, Math.max(d.triggerIndex() - 20, d.decisionIndex() - 79));
                            double[] w = Arrays.copyOfRange(s.y, from, (int) d.decisionIndex() + 1);
                            String id = m.name().toLowerCase() + "-" + seed + "-" + k;
                            byType.get(e.type()).add(new Incident(id, m.name().toLowerCase(), Bench.spec(m).rangeMin(), Bench.spec(m).rangeMax(),
                                    sigmas.get(i), d.cause(), d.triggerIndex(), d.decisionIndex(), triggers.get(i), d.evidence(), w, e.type().name(),
                                    e.type().cause()));
                            break;
                        }
                    }
                }
            }
        }
        Random rnd = new Random(20261006L);
        List<Incident> sample = new ArrayList<>();
        StringBuilder counts = new StringBuilder("fault,detected_events,sampled\n");
        for (FaultType f : FaultType.values()) {
            List<Incident> all = new ArrayList<>(byType.get(f));
            Collections.shuffle(all, rnd);
            List<Incident> take = all.subList(0, Math.min(perType, all.size()));
            sample.addAll(take);
            counts.append(f.name()).append(',').append(all.size()).append(',').append(take.size()).append('\n');
        }
        Files.createDirectories(Path.of("results"));
        StringBuilder jsonl = new StringBuilder();
        for (Incident i : sample) {
            jsonl.append(IncidentJson.write(i)).append('\n');
        }
        Files.writeString(Path.of("results/" + name + ".jsonl"), jsonl.toString(), StandardCharsets.UTF_8);
        Files.writeString(Path.of("results/" + name + "_counts.csv"), counts.toString(), StandardCharsets.UTF_8);
        System.out.print(counts);
        System.out.println("decisions in all runs: " + decisions + "; sampled incidents: " + sample.size());
    }
}
