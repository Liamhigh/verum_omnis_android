# Offline Model Research — August 2026

## Purpose

Web research (2026-08-07) into the best small offline models to run Verum Omnis
well on-device, with specific attention to the **triple verification** pipeline
(`TripleVerifier`: Thesis + Antithesis + Nine-Brain Synthesis) applied to
**law and strategy**. This document reviews the currently pinned models in
`core/Constitution.kt` against the August 2026 landscape and recommends
replacements.

Constraints honoured throughout:

- Runtime is llama.cpp via JNI (`voinference_jni.cpp`) → models must be GGUF.
- Everything runs offline on Android phones: entry (<4 GB RAM), mid (4–8 GB),
  flagship (8 GB+). See `ON_DEVICE_LLM_ARCHITECTURE.md`.
- Licenses must permit commercial redistribution (Apache 2.0 / MIT preferred).
- Triple verification needs **genuinely independent** models — different labs
  and different architectures, so a shared blind spot cannot produce false
  consensus on a legal finding.

---

## 1. Currently pinned models (Constitution.kt) vs the field

| Slot | Pinned today | Status Aug 2026 |
|------|--------------|-----------------|
| Report writer (G3), mid/flagship | Gemma 3 4B it, Q4_K_M (2.49 GB) | Superseded — Gemma 4 E4B beats it at a *smaller* memory footprint |
| Report writer, small devices | Gemma 3 1B it, Q4_K_M (0.81 GB) | Superseded by Gemma 4 E2B |
| Antithesis / communicator (PHR3) | **Phi-3 mini 4k** (2.39 GB) | Two generations old (early 2024). Weakest link in the triple check |
| Flagship communicator (G4) | Gemma 4 12B it, Q4_K_M (7.66 GB) | Current and real (released 2026-04-02); keep |

The Phi-3 mini pin is the most urgent problem: the antithesis leg of triple
verification is carried by a 2024-era model that measurably underperforms
every 2026 4B-class model on reasoning benchmarks.

## 2. The August 2026 small-model landscape

### Gemma 4 (Google DeepMind, released 2026-04-02, Apache 2.0)

Five sizes: **E2B** (~2.3B effective), **E4B** (~4.5B effective), **12B**,
**26B A4B** (MoE, ~3.8B active), **31B** dense. The E-series inherits the
Gemma 3n MatFormer design (nested sub-models, per-layer embeddings), so E4B
runs with roughly classic-4B memory while scoring well above it. Key facts:

- License changed to **Apache 2.0** this generation — removes the Gemma
  Terms-of-Use redistribution questions entirely.
- llama.cpp support landed at launch; Q4_K_M GGUFs are published (unsloth,
  bartowski).
- 128K context on E2B/E4B; **256K on 12B/26B/31B**.
- A deployment-aware evaluation (arXiv 2604.07035) across ARC-Challenge,
  GSM8K, Math L1–3 and TruthfulQA placed **Gemma 4 E4B and 26B A4B in the top
  accuracy cluster**, with E4B achieving the best accuracy-per-FLOP of all
  seven models tested — ahead of Qwen3-8B, Qwen3-30B-A3B, Phi-4-reasoning and
  Phi-4-mini-reasoning.

### Qwen 3.5 Small series (Alibaba, released 2026-03-02, Apache 2.0)

Four sizes: 0.8B, 2B, **4B**, **9B**. All natively multimodal with **262K
context**, using a Gated DeltaNet hybrid attention architecture (3:1 linear
to full attention) — constant-memory long context even on small models.

- Strongest multilingual small models available — relevant because legal
  evidence bundles are frequently not in English, and the LEXam legal
  benchmark found multilinguality is what let small Gemma models punch above
  their weight on law exams.
- llama.cpp support exists but is **recent**: Gated DeltaNet operators and
  GPU-offload metadata (`delta_net_gpu_compat`) require the latest llama.cpp
  builds. The vendored llama.cpp in `app/src/main/cpp` must be bumped before
  these models can ship.

### Phi-4-mini / Phi-4-mini-reasoning (Microsoft, MIT)

The natural in-place upgrade for the Phi-3 slot (3.8B, ~2.7 GB Q4_K_M GGUF).
However, the 2026 deployment study found Phi-4-mini-reasoning in the *lagging*
accuracy cluster, behind Gemma 4 E4B and the Qwen 3.5 generation. MIT license
and mature llama.cpp support remain its advantages.

### Legal-reasoning evidence specifically

- LEXam (340 law exams, arXiv 2505.12864) and related benchmarks show 4B-class
  models are the floor of acceptable legal reasoning — Gemma-3-4B was among
  the models that *failed* law exams outright, while Gemma-3-12B-it scored
  comparably to models ~33× larger, credited to multilingual training.
- Implication for Verum Omnis: this architecture is already correctly designed
  for that reality. The deterministic Nine-Brain engine extracts
  contradictions; the LLMs narrate, verify, and explain. 4B-class models are
  adequate for narration and adversarial checking, but the **flagship
  communicator handling statute mapping and strategy should stay at 12B**, and
  small-device users should see ordinal-confidence caps (already enforced:
  G3 candidates stay PENDING VERIFICATION without human sign-off).

## 3. Recommendations

### Recommended model set

| Slot | Replace | With | Q4_K_M size | Why |
|------|---------|------|-------------|-----|
| Report writer (G3), mid/flagship | Gemma 3 4B | **Gemma 4 E4B it** | ~2.5–3 GB | Top of the accuracy-per-FLOP table for its class; Apache 2.0; llama.cpp support since launch; 128K context lets the writer hold the full sealed ScanResult |
| Report writer, <6 GB RAM devices | Gemma 3 1B | **Gemma 4 E2B it** | ~1.5 GB | Same generation as the 4B writer (consistent narrative style), runs on ≤2 GB free RAM, audio-capable |
| Antithesis (PHR3 slot) | Phi-3 mini | **Qwen3.5-4B** | ~2.5 GB | Different lab (Alibaba) **and** different architecture (Gated DeltaNet) from the Gemma thesis leg — maximises independence of the triple check; best-in-class multilingual for non-English evidence; 262K context |
| Flagship communicator (G4) | — (keep) | **Gemma 4 12B it** | 7.66 GB | Already pinned and current; 256K context; the 12B tier is where credible statute/strategy reasoning starts per LEXam |

Fallback: if the vendored llama.cpp cannot be bumped to a Gated-DeltaNet-capable
build this cycle, use **Phi-4-mini (MIT)** as the antithesis instead of
Qwen3.5-4B — still a two-generation upgrade over Phi-3 with zero runtime risk —
and schedule the Qwen move for the next llama.cpp bump.

### Why this strengthens triple verification on laws and strategy

1. **True independence.** Thesis (Gemma 4 E4B, Google, MatFormer) and
   antithesis (Qwen3.5-4B, Alibaba, Gated DeltaNet) share no lab, no training
   pipeline, and no architecture. Agreement between them plus the
   deterministic Nine-Brain synthesis is meaningfully harder to satisfy than
   the current Gemma-3/Phi-3 pairing, where the antithesis model is too weak
   to mount a real challenge.
2. **Context headroom.** 128K/262K contexts mean both verification legs can
   see the *entire* sealed evidence bundle, not a truncated window — a
   contradiction check over partial context is not a check.
3. **Multilingual evidence.** Qwen3.5's multilingual strength covers the
   documented weakness of small models on non-English legal text.
4. **Strategy stays flagship-gated.** Strategic guidance remains a Gemma 4 12B
   capability on 8 GB+ devices, consistent with the benchmark evidence that
   sub-12B models fail law exams.

### Migration checklist (next PR)

1. Bump vendored llama.cpp to a build with Gemma 4 + Qwen3.5 (Gated DeltaNet)
   support; re-run `LlamaModelSmokeTest` and `NativeBridgeAvailabilityTest`.
2. Download each GGUF, compute SHA-256, and update the
   `MODEL_*_URL/SHA256/SIZE_BYTES` constants in `core/Constitution.kt`
   (constitutional requirement: hashes are compile-time constants; never
   trust a hash you did not compute from the artifact you will ship).
3. Update `ModelCatalog.kt` slots (`GEMMA_3` → Gemma 4 E4B, `GEMMA_3_1B` →
   E2B, `PHI_3` → Qwen3.5-4B or Phi-4-mini) — the shared-id/shared-path
   design means the rest of the pipeline is unaffected.
4. Update `TripleVerifier` status labels (`gemma3Status`/`phi3Status`) to the
   new model names, and `ON_DEVICE_LLM_ARCHITECTURE.md` sections 3–6.
5. Re-benchmark on the three device tiers and refresh section 9 of the ODLA
   doc with measured numbers.

## 4. Sources

- [Gemma 4 model overview — Google AI for Developers](https://ai.google.dev/gemma/docs/core)
- [Gemma 4 model card — Google AI for Developers](https://ai.google.dev/gemma/docs/core/model_card_4)
- [Gemma 4 Technical Report (arXiv)](https://arxiv.org/pdf/2607.02770)
- [Gemma 4, Phi-4, and Qwen3: Accuracy–Efficiency Tradeoffs (arXiv 2604.07035)](https://arxiv.org/html/2604.07035v1)
- [Gemma 4 Guide: E2B, E4B, 26B MoE & 31B (codersera)](https://codersera.com/blog/gemma-4-complete-guide-2026/)
- [Gemma 4 — How to Run Locally (Unsloth)](https://unsloth.ai/docs/models/gemma-4)
- [google/gemma-4-12B-it — Hugging Face](https://huggingface.co/google/gemma-4-12B-it)
- [Qwen 3.5 small models — Artificial Analysis](https://artificialanalysis.ai/articles/qwen3-5-small-models)
- [Alibaba releases Qwen 3.5 Small models 0.8B–9B (MarkTechPost)](https://www.marktechpost.com/2026/03/02/alibaba-just-released-qwen-3-5-small-models-a-family-of-0-8b-to-9b-parameters-built-for-on-device-applications/)
- [unsloth/Qwen3.5-4B-MTP-GGUF — Hugging Face](https://huggingface.co/unsloth/Qwen3.5-4B-MTP-GGUF)
- [Introduction to Qwen3.5 — vLLM and llama.cpp (DebuggerCafe)](https://debuggercafe.com/introduction-to-qwen3-5-overview-vllm-and-llama-cpp/)
- [LEXam: Benchmarking Legal Reasoning on 340 Law Exams (arXiv 2505.12864)](https://arxiv.org/pdf/2505.12864)
- [Phi-4-Mini Technical Report (arXiv 2503.01743)](https://arxiv.org/pdf/2503.01743)
- [Gemma 3n model overview — Google AI for Developers](https://ai.google.dev/gemma/docs/gemma-3n)
- [The Best Open-Source Small Language Models in 2026 (BentoML)](https://www.bentoml.com/blog/the-best-open-source-small-language-models)
- [Best Mobile LLM 2026: Phi-4 Mini vs Gemma 3 vs SmolLM (PromptQuorum)](https://www.promptquorum.com/power-local-llm/mobile-llm-models-phi4-gemma-smollm)
