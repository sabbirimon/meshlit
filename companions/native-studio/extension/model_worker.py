# SPDX-License-Identifier: Apache-2.0
"""One bounded generation request in an explicitly selected local Python environment."""
import contextlib
import importlib.metadata
import json
import os
import platform
import sys
from pathlib import Path

PACKAGES = {
    "flax-transformers": ("transformers", "flax", "jax", "jaxlib"),
    "transformers": ("transformers", "torch", "safetensors"),
    "onnx-genai": ("onnxruntime-genai",),
    "openvino-genai": ("openvino-genai",),
    "mlx-lm": ("mlx-lm",),
}


def prompt_for(messages):
    if len(messages) == 1:
        return messages[0]["content"]
    return "\n".join(m["role"] + ": " + m["content"] for m in messages) + "\nassistant:"


def generate(adapter, root, messages, limit):
    prompt = prompt_for(messages)
    if adapter == "flax-transformers":
        from transformers import AutoTokenizer, FlaxAutoModelForCausalLM
        options = dict(local_files_only=True, trust_remote_code=False)
        tokenizer = AutoTokenizer.from_pretrained(root, **options)
        model = FlaxAutoModelForCausalLM.from_pretrained(root, **options)
        if tokenizer.chat_template:
            prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        encoded = tokenizer(prompt, return_tensors="np", add_special_tokens=not bool(tokenizer.chat_template))
        ids = encoded["input_ids"]
        length = ids.shape[-1]
        if not 0 < length <= 8192:
            raise ValueError("Input exceeds the adapter context limit")
        result = model.generate(ids, attention_mask=encoded["attention_mask"], max_new_tokens=limit, do_sample=False)
        return tokenizer.decode(result.sequences[0, length:], skip_special_tokens=True)
    if adapter == "transformers":
        from transformers import AutoModelForCausalLM, AutoTokenizer
        import torch
        options = dict(local_files_only=True, trust_remote_code=False)
        tokenizer = AutoTokenizer.from_pretrained(root, **options)
        model = AutoModelForCausalLM.from_pretrained(root, use_safetensors=True, **options).eval()
        if tokenizer.chat_template:
            ids = tokenizer.apply_chat_template(messages, add_generation_prompt=True, return_tensors="pt")
        else:
            ids = tokenizer(prompt, return_tensors="pt").input_ids
        if ids.shape[-1] > 8192:
            raise ValueError("Input exceeds the adapter context limit")
        with torch.inference_mode():
            result = model.generate(ids, attention_mask=torch.ones_like(ids), max_new_tokens=limit, do_sample=False)
        return tokenizer.decode(result[0, ids.shape[-1]:], skip_special_tokens=True)
    if adapter == "mlx-lm":
        from mlx_lm import generate as mlx_generate, load
        with (root / "config.json").open("rb") as config:
            contents = config.read(8 * 1024 * 1024 + 1)
        if len(contents) > 8 * 1024 * 1024 or json.loads(contents).get("model_file") is not None:
            raise ValueError("MLX generation requires a built-in model architecture")
        model, tokenizer = load(str(root), tokenizer_config={"trust_remote_code": False})
        if tokenizer.chat_template:
            prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        return mlx_generate(model, tokenizer, prompt=prompt, max_tokens=limit, verbose=False)
    if adapter == "openvino-genai":
        import openvino_genai as ov
        pipeline = ov.LLMPipeline(str(root), "CPU")
        return pipeline.generate(prompt, max_new_tokens=limit, do_sample=False)
    import onnxruntime_genai as og
    model = og.Model(str(root))
    tokenizer = og.Tokenizer(model)
    ids = tokenizer.encode(prompt)
    if len(ids) > 8192:
        raise ValueError("Input exceeds the adapter context limit")
    params = og.GeneratorParams(model)
    params.set_search_options(max_length=len(ids) + limit, do_sample=False)
    generator = og.Generator(model, params)
    generator.append_tokens(ids)
    for _ in range(limit):
        if generator.is_done():
            break
        generator.generate_next_token()
    return tokenizer.decode(generator.get_sequence(0)[len(ids):])


def respond(req):
    adapter = req["adapter"]
    packages = PACKAGES[adapter]
    if adapter == "mlx-lm" and (platform.system(), platform.machine()) != ("Darwin", "arm64"):
        raise ValueError("This MLX-LM adapter requires an Apple Silicon Python environment")
    if req["operation"] == "probe":
        if adapter == "flax-transformers":
            from transformers import FlaxAutoModelForCausalLM  # Verify the installed Transformers exposes Flax.
        return {"versions": {name: importlib.metadata.version(name) for name in packages}}
    if req["operation"] != "generate":
        raise ValueError("Unknown worker operation")
    root = Path(req["file"])
    messages = req["messages"]
    limit = req.get("max_tokens", 128)
    if not root.is_absolute() or not root.is_dir() or root.is_symlink():
        raise ValueError("Choose a local model package directory")
    if not isinstance(messages, list) or not 1 <= len(messages) <= 9 or any(
        m["role"] not in ("user", "assistant") or not isinstance(m["content"], str)
        for m in messages
    ) or sum(len(m["content"]) for m in messages) > 128000:
        raise ValueError("Invalid conversation")
    if type(limit) != int or not 1 <= limit <= 128:
        raise ValueError("Output limit must be between 1 and 128 tokens")
    text = generate(adapter, root, messages, limit)
    if not isinstance(text, str) or not text.strip():
        raise ValueError("Runtime generated no displayable text")
    return {"text": text}


if __name__ == "__main__":
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1")
    try:
        data = sys.stdin.buffer.read(1024 * 1024 + 1)
        if len(data) > 1024 * 1024:
            raise ValueError("Request too large")
        with contextlib.redirect_stdout(sys.stderr):
            answer = respond(json.loads(data))
        print(json.dumps(answer))
    except Exception as error:
        print(json.dumps({"error": str(error)[:600] or type(error).__name__}))
        sys.exit(1)
