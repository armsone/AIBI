# DenimDex iPhone repeat verification

- Date: 2026-10-03 KST; app0.3.3/build202610030015; AIBI0.5.2.
- Physical iPhone17Pro; data-preserving update install and launch, then two terminate/relaunch cycles.
- Current DenimDex product exposes ChatGPT appraisal only; no new Gemini/Claude product feature added.
- Three requests with one public synthetic blue image completed and each result applied once.
- Native and browser layers each record send_attempted for the same attempt1; there are two stage records, not evidence of two sends. Exactly one request_started event with request_has_messages=1 was observed per run.
- No prompts, full answers, personal photos, credentials or raw DOM recorded. Synthetic photo does not validate denim valuation accuracy.
- Android build evidence is separate; no connected Android device or mDNS target was available.

| Run ID | Duration ms | Message requests | Results |
|---|---:|---:|---:|
| A2E69F97-2C08-40C1-A284-058E301588B2 | 37308 | 1 | 1 |
| 10B30911-7F89-4904-9780-AD3ADD7E429D | 42813 | 1 | 1 |
| 0E43F221-27E6-4E07-818F-3AC8EB082857 | 31744 | 1 | 1 |
