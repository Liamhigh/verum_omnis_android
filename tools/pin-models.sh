#!/usr/bin/env bash
# Pins the 2026-08 refresh models (OFFLINE_MODEL_RESEARCH_2026-08.md).
#
# Downloads each GGUF artifact, computes its SHA-256 and byte size, and prints
# the Kotlin constants to paste into core/Constitution.kt. Run this from a
# machine with unrestricted Hugging Face access — some dev/CI sandboxes block
# huggingface.co, which is exactly why these constants ship as PENDING.
#
# Constitutional rule: a hash is computed from the artifact you will ship,
# never copied from a webpage. This script is that computation.
#
# If a URL 404s, the quantizer renamed the file — find the Q4_K_M .gguf in the
# same Hugging Face repo, update the URL here AND in Constitution.kt, re-run.
set -euo pipefail

WORKDIR="${1:-./model-pins}"
mkdir -p "$WORKDIR"

pin() {
    local const_prefix="$1" url="$2"
    local file="$WORKDIR/$(basename "$url")"
    echo "== $const_prefix" >&2
    curl -fL --retry 3 --retry-delay 5 -C - -o "$file" "$url" >&2
    local sha size
    sha=$(sha256sum "$file" | cut -d' ' -f1)
    size=$(stat -c%s "$file" 2>/dev/null || stat -f%z "$file")
    printf '    const val %s_URL =\n        "%s"\n' "$const_prefix" "$url"
    printf '    const val %s_SHA256 = "%s"\n' "$const_prefix" "$sha"
    printf '    const val %s_SIZE_BYTES = %sL\n\n' "$const_prefix" "$size"
}

pin MODEL_GEMMA4_E4B "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf"
pin MODEL_GEMMA4_E2B "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf"
pin MODEL_QWEN35_4B  "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf"

echo "Paste the constants above into app/src/main/java/com/verumomnis/forensic/core/Constitution.kt," >&2
echo "replacing the PENDING placeholders. ModelCatalog activates each spec automatically once pinned." >&2
