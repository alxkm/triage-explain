# triage-explain

Code and results for the paper *Detectors Decide, the Model Explains: Grounded Operator Explanations for
Telemetry Triage with Local Language Models*.

Deterministic detectors ([telemetry-triage](https://github.com/alxkm/telemetry-triage)) decide whether a departing
reading reflects a **process** change, an **instrument** fault or **data-path** corruption. A local language model only
writes the explanation. Six grounding rules admit or reject each explanation; a template is the fallback:

| Rule | Requirement |
|---|---|
| R1 | one JSON object with `cause`, `explanation`, `next_check` |
| R2 | `cause` is the decided cause |
| R3 | every number in the explanation occurs in the facts (rounding allowed) |
| R4 | `next_check` is a permitted check for the decided cause |
| R5 | no unnegated claim of another cause (lexical) |
| R6 | at most 80 words |

The model is reached through Spring AI or LangChain4j (both against Ollama's OpenAI-compatible endpoint), with the
reliability layer [llm-call-guard](https://github.com/alxkm/llm-call-guard) installed.

## Results (6 October 2026)

- **X1** — 260 incidents × 5 model/framework combinations: 1,298 of 1,300 explanations accepted; a measurement from
  the evidence is quoted in 14 % (llama3.2) to 64 % (qwen2.5:32b) of them.
- **X2** — the model as attributor, on the same incidents: 44–62 % correct from the detectors' evidence, 5–39 % from
  the raw readings; the detectors: 90.0 %.
- **X3** — model server failing: every explanation fell back to the template, within at most 10.3 s.

Details: `results/summary.md`.

## Reproduce

Requirements: JDK 21, Maven 3.9, a local [Ollama](https://ollama.com) with `llama3.2`, `qwen2.5:7b`, `mistral:7b`,
`qwen2.5:32b`. Install the two dependencies first (`mvn install` in `telemetry-triage` and in `llm-call-guard`).

```
mvn -q package
CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)"
java -cp "$CP" explain.bench.ExportIncidents 20                       # results/incidents.jsonl (test seeds)
java -cp "$CP" explain.bench.Experiments X1 spring-ai:llama3.2 spring-ai:qwen2.5:7b spring-ai:mistral:7b langchain4j:qwen2.5:7b spring-ai:qwen2.5:32b
java -cp "$CP" explain.bench.Experiments X2 spring-ai:llama3.2 spring-ai:qwen2.5:7b spring-ai:mistral:7b spring-ai:qwen2.5:32b
java -cp "$CP" explain.bench.Experiments X3
python scripts/summarize.py
```

Runs append row by row and resume where they stopped. `INCIDENTS=incidents_dev` runs on the development set.

## Protocol

- Rules, prompt and facts format were fixed on 65 development incidents (seeds 1000–1019); every change is listed in
  `docs/DEVELOPMENT.md`. The 260 test incidents (seeds 2000–2049, 20 per fault type) were run after that.
- X2 was interrupted once when the model server stopped; the nine calls made while it was down were removed and
  repeated after a restart (`results/environment_X2.txt` lists both starts).
- `results/audit_sheet.csv`: 50 accepted explanations (10 per model and framework) for a human rating of
  faithfulness, not yet filled in.

## Licence

MIT.
