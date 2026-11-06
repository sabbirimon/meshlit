"""Pinned desktop-only model catalog and artifact checks; no downloads at import."""
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit


def load_catalog(file):
    catalog = json.loads(Path(file).read_text())
    models = catalog.get('models', [])
    if catalog.get('schema') != 1 or not isinstance(models, list) or not 1 <= len(models) <= 16:
        raise ValueError('Invalid Studio model catalog')
    ids, filenames = set(), set()
    for model in models:
        if not isinstance(model, dict) or not re.fullmatch(r'[a-z0-9][a-z0-9.-]{0,63}', model.get('id', '')):
            raise ValueError('Invalid Studio model id')
        filename = model.get('filename', '')
        if not re.fullmatch(r'[A-Za-z0-9_.-]+\.gguf', filename) or filename.startswith('.'):
            raise ValueError('Invalid Studio model filename')
        if model['id'] in ids or filename in filenames:
            raise ValueError('Duplicate Studio model')
        ids.add(model['id']); filenames.add(filename)
        if not isinstance(model.get('sizeBytes'), int) or isinstance(model['sizeBytes'], bool) or not 4 <= model['sizeBytes'] <= 32 * 1024**3:
            raise ValueError('Invalid model size')
        if not re.fullmatch(r'[a-f0-9]{64}', model.get('sha256', '')) or not re.fullmatch(r'[a-f0-9]{40}', model.get('revision', '')):
            raise ValueError('Model must have a pinned revision and SHA-256')
        url = urlsplit(model.get('url', ''))
        if url.scheme != 'https' or url.netloc != 'huggingface.co' or url.query or url.fragment or not url.path.endswith('/resolve/' + model['revision'] + '/' + filename):
            raise ValueError('Model URL must bind the pinned revision and filename over HTTPS')
    if catalog.get('defaultModel') not in ids:
        raise ValueError('Default model is absent')
    return catalog


def verify_model(file, model):
    file = Path(file)
    if file.is_symlink() or not file.is_file() or file.stat().st_size != model['sizeBytes']:
        raise ValueError('Missing or wrong-sized Studio model: ' + model['id'])
    digest = hashlib.sha256()
    with file.open('rb') as source:
        if source.read(4) != b'GGUF':
            raise ValueError('Studio model is not GGUF: ' + model['id'])
        source.seek(0)
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != model['sha256']:
        raise ValueError('Studio model SHA-256 mismatch: ' + model['id'])


def verify_catalog(directory, catalog):
    for model in catalog['models']:
        verify_model(Path(directory) / model['filename'], model)
