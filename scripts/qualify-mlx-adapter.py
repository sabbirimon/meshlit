"""Generate from a deterministic quantized MLX decoder on an Apple Silicon runner."""
import json
import platform
import subprocess
import sys
import tempfile
from pathlib import Path

import mlx.core as mx
import mlx.nn as nn
from mlx.utils import tree_map
from mlx_lm.models.llama import Model, ModelArgs
from tokenizers import Tokenizer
from tokenizers.models import WordLevel
from transformers import PreTrainedTokenizerFast


def main():
    if (platform.system(), platform.machine()) != ("Darwin", "arm64"):
        raise RuntimeError("This qualification requires Apple Silicon")
    with tempfile.TemporaryDirectory() as temporary:
        folder = Path(temporary)
        config = dict(model_type="llama", hidden_size=64, intermediate_size=128,
                      num_hidden_layers=1, num_attention_heads=2, vocab_size=4,
                      rms_norm_eps=1e-5, tie_word_embeddings=True)
        model = Model(ModelArgs(**config))
        model.update(tree_map(mx.zeros_like, model.parameters()))
        model.model.norm.weight = mx.ones((64,))
        # 'Hello' and 'world' point in the same direction. Tied output weights
        # make 'world' score highest on each step even after 4-bit quantization.
        direction = mx.array([1., -1.] * 32)
        model.model.embed_tokens.weight = mx.stack([direction*0,direction,direction*2,direction*0])
        nn.quantize(model, group_size=64, bits=4)
        model.save_weights(str(folder / 'model.safetensors'))
        config.update(quantization=dict(group_size=64, bits=4), eos_token_id=3)
        (folder / 'config.json').write_text(json.dumps(config))
        tokenizer = Tokenizer(WordLevel({'<unk>':0,'Hello':1,'world':2,'<eos>':3},unk_token='<unk>'))
        PreTrainedTokenizerFast(tokenizer_object=tokenizer, unk_token='<unk>', eos_token='<eos>').save_pretrained(folder)
        entry = Path(__file__).resolve().parents[1] / 'companions/native-studio/extension/model-adapters.cjs'
        args = ['node',str(entry),'generate',str(folder),'mlx-lm',sys.executable]
        request = {'messages':[{'role':'user','content':'Hello'}], 'max_tokens':4}
        output = json.loads(subprocess.check_output(args,input=json.dumps(request),text=True))
        if output['text'].replace(' ','').strip() != 'world'*4:
            raise AssertionError(f"Unexpected generated output: {output}")
        print(platform.machine(), mx.default_device(), output)


if __name__ == '__main__':
    main()
