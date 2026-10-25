# Author's rating of accepted explanations (X1)

Sample: 50 explanations accepted by rules R1-R6, 10 per model and framework, drawn with seed 20261006 by
`scripts/summarize.py`. Rated by the author, one rater. Notes in `audit_sheet.csv` are the rater's, in Russian.

| Model / framework | Rated | Faithful | Unsupported claim |
|---|---|---|---|
| langchain4j/qwen2.5:7b | 10 | 8 | 2 |
| spring-ai/llama3.2 | 10 | 10 | 0 |
| spring-ai/mistral:7b | 10 | 10 | 0 |
| spring-ai/qwen2.5:32b | 10 | 10 | 1 |
| spring-ai/qwen2.5:7b | 10 | 8 | 2 |
| **total** | 50 | 46 | 5 |

## Notes, grouped (English summary of the rater's notes)

- **Unfaithful (4, all qwen2.5:7b, two per framework):** three describe evidence of noise growth as a bias
  (one adds "drift or stuck value"); one describes an abrupt step above the plausible rate as "bias or drift".
- **Unsupported but faithful (1, qwen2.5:32b):** lists every instrument mechanism for a single abrupt step.
- **Generic (8 of 10 llama3.2):** restates the definition of the cause without using the evidence.
- **Narrowed (7):** data-path explanations name one kind of corruption where the evidence lists three alternatives.
- **Prejudged (1, qwen2.5:32b):** calls an undetermined decision a sensor issue.
- **Misattributed number (2, qwen2.5:7b via LangChain4j):** quotes the 40 s between first trigger and decision
  as the duration of a stuck value; the prompt states it as the decision delay (`Facts.java`).
