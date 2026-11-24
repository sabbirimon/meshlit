const actions = Object.freeze({
  local: 'Local model', cloud: 'Online provider', ollama: 'Ollama',
  lmstudio: 'LM Studio', bionic: 'LM Studio Bionic', device: 'Pair a device',
  management: 'Dashboards', ide: 'Coding IDE'
});

// This function also runs in the isolated chat webview.
function setupIntent(input) {
  if (typeof input !== 'string' || input.length > 100) return;
  const text = input.trim().toLowerCase().replace(/^(?:please|can you|could you)\s+/, '')
    .replace(/[.!?]+$/, '').replace(/\s+/g, ' ');
  if (/^(?:pair|connect(?: to)?) (?:a |my |the )?(?:local |remote )?(?:device|phone|laptop|desktop)$/.test(text)) return 'device';
  if (/^(?:load|choose|use|select) (?:a |the |my )?local model$/.test(text)) return 'local';
  if (/^(?:connect(?: to)?|choose|use|select) (?:an? |the )?(?:online|cloud) (?:model|provider)$/.test(text)) return 'cloud';
  if (/^(?:connect(?: to)?|use) ollama$/.test(text)) return 'ollama';
  if (/^(?:connect(?: to)?|use) lm studio$/.test(text)) return 'lmstudio';
  if (/^(?:connect(?: to)?|use) (?:lm (?:studio )?)?bionic$/.test(text)) return 'bionic';
  if (/^open (?:the )?(?:management )?dashboard$/.test(text)) return 'management';
  if (/^open (?:the )?(?:coding )?ide$/.test(text)) return 'ide';
}

module.exports = {actions, setupIntent};
