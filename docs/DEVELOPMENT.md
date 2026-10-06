# Changes made on development data

The grounding rules, the prompt and the facts format were fixed on the **development incidents** only:
`results/incidents_dev.jsonl`, 65 incidents (5 per fault type) from the triage method's development seeds 1000–1019,
which article 1 also used for development. The 260 test incidents (seeds 2000–2049) were run once, after the
changes below.

| # | Observed on development data | Change |
|---|---|---|
| 0 | (before any model run) four incidents from the test sample were used for a smoke test of the pipeline on 6 October 2026; the run exposed change 1 | the smoke-test replies were discarded; development then moved to the separate seeds |
| 1 | a model wrote the decided cause followed by its gloss: `"DATA_PATH (the value was corrupted …)"` | R2 compares the leading cause label; a reply that replaces the label by the gloss alone is still rejected |
| 2 | an UNDETERMINED explanation that names the hypotheses it cannot separate ("process trend and sensor drift cannot be separated") was rejected by R5 (1 of 65 for llama3.2 and for mistral:7b) | R5 does not apply to the process and instrument hypotheses when the decision is UNDETERMINED |
| 3 | readings of a DROPOUT event are missing (NaN), which JSON cannot carry | missing readings are stored as `null` and shown to a model as `missing` |

Not changed, although considered: a rule rejecting a fault mechanism (bias, drift, stuck …) not named in the evidence.
On development data it would also reject correct inferences — a persistent jump is reported by the detectors as a
jump, and a model that calls it a bias is right — so semantic faithfulness is left to the author's audit
(`results/audit_sheet.csv`) instead of an automatic rule.
