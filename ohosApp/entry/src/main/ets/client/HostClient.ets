import { http } from '@kit.NetworkKit';
import { url } from '@kit.ArkTS';

export interface Turn { role: string; content: string; }
interface ModelItem { id: string; }
interface ModelsResponse { data: ModelItem[]; }
interface CompletionMessage { content: string; }
interface CompletionChoice { message: CompletionMessage; }
interface TokenUsage { completion_tokens: number; }
interface CompletionResponse { choices: CompletionChoice[]; usage?: TokenUsage; }
export interface HostReply { content: string; tokens?: number; elapsedMs: number; }

/** NEXT-only API 26 target: system TLS, no redirects/cache, token retained only in memory. */
export class HostClient {
  private active?: http.HttpRequest;
  private revision: number = 0;
  static validateEndpoint(value: string): string {
    if (value.length > 2048 || /\s|[\u0000-\u001f]/.test(value)) throw new Error('INVALID_ENDPOINT');
    const parsed = new url.URL(value);
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash ||
      (parsed.pathname !== '/v1' && parsed.pathname !== '/v1/')) throw new Error('HTTPS_V1_REQUIRED');
    return value.replace(/\/$/, '');
  }
  cancel(): void { this.revision++; this.active?.destroy(); this.active = undefined; }
  private async request(endpoint: string, token: string, path: string, body?: string): Promise<string> {
    if (this.active || !token || token.length > 8192 || /[\u0000-\u001f\u007f]/.test(token)) throw new Error('INVALID_REQUEST');
    const base = HostClient.validateEndpoint(endpoint);
    const revision = this.revision;
    const request = http.createHttp(); this.active = request;
    // The whole-operation timer also bounds stalled response bodies.
    const timeout = setTimeout(() => { request.destroy(); }, 120000);
    try {
      const response = await request.request(base + '/' + path, {
        method: body ? http.RequestMethod.POST : http.RequestMethod.GET,
        header: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json', 'Accept': 'application/json' },
        extraData: body ?? '', expectDataType: http.HttpDataType.STRING,
        usingCache: false, maxLimit: 262144, connectTimeout: 15000, readTimeout: 120000, maxRedirects: 0
      });
      if (revision !== this.revision) throw new Error('CANCELLED');
      if (response.responseCode !== 200 || typeof response.result !== 'string') throw new Error('HOST_FAILED');
      return response.result as string;
    } finally { clearTimeout(timeout); request.destroy(); if (this.active === request) this.active = undefined; }
  }
  private parseObject(text: string): Object {
    if (text.length > 262144) throw new Error('RESPONSE_LIMIT');
    let depth = 0; let quoted = false; let escaped = false;
    for (let index = 0; index < text.length; index++) {
      const character = text[index];
      if (quoted) {
        if (escaped) escaped = false;
        else if (character === '\\') escaped = true;
        else if (character === '"') quoted = false;
      } else if (character === '"') quoted = true;
      else if (character === '{' || character === '[') { depth++; if (depth > 32) throw new Error('JSON_DEPTH'); }
      else if (character === '}' || character === ']') { depth--; if (depth < 0) throw new Error('JSON_INVALID'); }
    }
    if (depth !== 0 || quoted) throw new Error('JSON_INVALID');
    return JSON.parse(text) as Object;
  }
  async models(endpoint: string, token: string): Promise<string[]> {
    const data = this.parseObject(await this.request(endpoint, token, 'models')) as ModelsResponse;
    if (!data.data || !Array.isArray(data.data) || data.data.length > 512) throw new Error('INVALID_MODELS');
    const models: string[] = [];
    data.data.forEach((item: ModelItem) => {
      if (typeof item.id !== 'string' || !item.id || item.id.length > 512 || /[\u0000-\u001f]/.test(item.id)) throw new Error('INVALID_MODEL');
      if (!models.includes(item.id)) models.push(item.id);
    });
    return models;
  }
  async generate(endpoint: string, token: string, model: string, turns: Turn[], maximum: number): Promise<HostReply> {
    if (model.length < 1 || model.length > 512 || !Number.isInteger(maximum) || maximum < 64 || maximum > 4096 ||
      turns.length < 1 || turns.length > 64 || turns[turns.length - 1].role !== 'user' ||
      turns.some((turn: Turn) => !['user', 'assistant'].includes(turn.role)) ||
      turns.reduce((size: number, turn: Turn) => size + turn.content.length, 0) > 131072) throw new Error('CONTEXT_LIMIT');
    const start = Date.now();
    const payload = JSON.stringify({ model: model, messages: turns, stream: false, max_tokens: maximum });
    const data = this.parseObject(await this.request(endpoint, token, 'chat/completions', payload)) as CompletionResponse;
    if (!Array.isArray(data.choices) || data.choices.length < 1 || typeof data.choices[0].message?.content !== 'string') throw new Error('INVALID_REPLY');
    const content = data.choices[0].message.content;
    if (content.length > 131072) throw new Error('REPLY_LIMIT');
    const count = data.usage?.completion_tokens;
    const reported = count !== undefined && Number.isInteger(count) && count >= 0 && count <= 10000000 ? count : undefined;
    return { content: content, tokens: reported, elapsedMs: Date.now() - start };
  }
}
