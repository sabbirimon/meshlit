import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { HostClient, Turn, HostReply } from '../client/HostClient';
import { capabilities } from 'libmeshlit_runtime.so';

interface VisualTheme { name: string; background: string; surface: string; foreground: string; accent: string; }
const THEMES: VisualTheme[] = [
  { name: 'Graphite', background: '#101113', surface: '#202226', foreground: '#F1F2F3', accent: '#E5E7EB' },
  { name: 'Paper', background: '#F9FAFB', surface: '#EDF0F3', foreground: '#17191D', accent: '#2563EB' },
  { name: 'Aurora', background: '#121523', surface: '#22283B', foreground: '#F2F4FF', accent: '#8AB4FF' },
  { name: 'Studio', background: '#101113', surface: '#242426', foreground: '#F1F2F3', accent: '#FF965C' }
];
@Entry
@Component
struct Index {
  @State private language: string = 'en';
  @State private themeIndex: number = 3;
  @State private fontScale: number = 1;
  @State private settings: boolean = false;
  @State private network: boolean = false;
  @State private endpoint: string = '';
  @State private token: string = '';
  @State private models: string[] = [];
  @State private selectedModel: number = 0;
  @State private prompt: string = '';
  @State private messages: Turn[] = [];
  @State private busy: boolean = false;
  @State private error: boolean = false;
  @State private outputLimit: number = 2048;
  @State private usage: string = '';
  @State private nativeAvailable: boolean = false;
  private store?: preferences.Preferences;
  private client: HostClient = new HostClient();
  private revision: number = 0;
  private t(en: string, zh: string): string { return this.language === 'zh-Hans' ? zh : en; }
  private palette(): VisualTheme { return THEMES[this.themeIndex]; }
  async aboutToAppear(): Promise<void> {
    try {
      this.store = await preferences.getPreferences(this.getUIContext().getHostContext() as common.UIAbilityContext, { name: 'meshlit-next-appearance' });
      const language = await this.store.get('language', 'en') as string;
      this.language = language === 'zh-Hans' ? language : 'en';
      const theme = await this.store.get('theme', 3) as number;
      this.themeIndex = Number.isInteger(theme) && theme >= 0 && theme < THEMES.length ? theme : 3;
      const scale = await this.store.get('fontScale', 1) as number;
      this.fontScale = Number.isFinite(scale) ? Math.min(1.5, Math.max(0.85, scale)) : 1;
      this.nativeAvailable = capabilities().localInference;
    } catch (_) { this.error = true; }
  }
  aboutToDisappear(): void { this.stop(); this.token = ''; this.messages = []; }
  private async save(): Promise<void> {
    try {
      if (!this.store) throw new Error('PREFERENCES_UNAVAILABLE');
      await this.store.put('language', this.language); await this.store.put('theme', this.themeIndex);
      await this.store.put('fontScale', this.fontScale); await this.store.flush();
    } catch (_) { this.error = true; }
  }
  private stop(): void { this.revision++; this.client.cancel(); this.busy = false; }
  private async connect(): Promise<void> {
    if (!this.network || this.busy) return;
    this.busy = true; this.error = false; this.models = []; const revision = this.revision;
    try { const models = await this.client.models(this.endpoint, this.token); if (revision === this.revision) { this.models = models; this.selectedModel = 0; } }
    catch (_) { if (revision === this.revision) this.error = true; }
    finally { if (revision === this.revision) this.busy = false; }
  }
  private async send(): Promise<void> {
    if (!this.network || this.busy || !this.prompt.trim() || this.models.length === 0) return;
    const turns: Turn[] = [...this.messages, { role: 'user', content: this.prompt.trim() }];
    this.busy = true; this.error = false; const revision = this.revision;
    try {
      const reply: HostReply = await this.client.generate(this.endpoint, this.token, this.models[this.selectedModel], turns, this.outputLimit);
      if (revision !== this.revision) return;
      this.messages = [...turns, { role: 'assistant', content: reply.content }]; this.prompt = '';
      const rate = reply.tokens !== undefined && reply.tokens > 0 && reply.elapsedMs > 0 ? (reply.tokens * 1000 / reply.elapsedMs).toFixed(2) : this.t('unknown', '未知');
      this.usage = this.t('Reported tokens', '报告的令牌') + ': ' + (reply.tokens ?? this.t('unknown', '未知')) + ' · ' + this.t('End-to-end tokens/s', '端到端令牌/秒') + ': ' + rate;
    } catch (_) { if (revision === this.revision) this.error = true; }
    finally { if (revision === this.revision) this.busy = false; }
  }
  @Builder private SettingsPanel() {
    Column({ space: 12 }) {
      Image($r('app.media.settings')).width(28).height(28)
      Text(this.t('Make it yours', '个性化设置')).fontSize(26).fontWeight(FontWeight.Bold).fontColor(this.palette().foreground)
      Row({ space: 12 }) {
        Button((this.language === 'en' ? '✓ ' : '') + 'English').onClick(() => { this.language = 'en'; this.save(); })
        Button((this.language === 'zh-Hans' ? '✓ ' : '') + '简体中文').onClick(() => { this.language = 'zh-Hans'; this.save(); })
      }
      Row({ space: 8 }) { ForEach(THEMES, (theme: VisualTheme, index: number) => {
        Button((index === this.themeIndex ? '✓ ' : '') + theme.name).onClick(() => { this.themeIndex = index; this.save(); })
      }, (theme: VisualTheme) => theme.name) }
      Text(this.t('Text size', '字体大小')).fontColor(this.palette().foreground)
      Slider({ value: this.fontScale, min: 0.85, max: 1.5, step: 0.05 }).onChange((value: number) => { this.fontScale = value; this.save(); })
      Text(this.t('Explicit host access', '明确授权主机访问')).fontColor(this.palette().foreground).fontSize(20)
      Row({ space: 12 }) {
        Toggle({ type: ToggleType.Switch, isOn: this.network }).onChange((value: boolean) => { this.network = value; if (!value) { this.stop(); this.models = []; } })
        Text(this.t('Allow selected host', '允许所选主机')).fontColor(this.palette().foreground)
      }
      Text(this.t('Prompts and token go to this HTTPS host. No redirects. Token and chats are session-only.', '提示词和令牌将发送至此 HTTPS 主机。禁止重定向。令牌和聊天仅保留在当前会话。')).fontColor(this.palette().foreground).fontSize(13)
      TextInput({ text: this.endpoint, placeholder: 'https://host:port/v1' }).onChange((value: string) => { this.stop(); this.endpoint = value.slice(0, 2048); this.models = []; })
      TextInput({ text: this.token, placeholder: this.t('Client token', '客户端令牌') }).type(InputType.Password).onChange((value: string) => { this.stop(); this.token = value.slice(0, 8192); this.models = []; })
      Button(this.t('Connect / refresh models', '连接 / 刷新模型')).enabled(this.network && !this.busy).onClick(() => { this.connect(); })
      if (this.models.length > 0) { Select(this.models.map((model: string) => ({ value: model }))).selected(this.selectedModel).onSelect((index: number) => { this.selectedModel = index; }) }
      Text(this.t('Output limit', '输出上限') + ': ' + this.outputLimit).fontColor(this.palette().foreground)
      Slider({ value: this.outputLimit, min: 64, max: 4096, step: 64 }).onChange((value: number) => { this.outputLimit = Math.round(value); })
      Text(this.nativeAvailable ? this.t('Native engine available', '本地引擎可用') : this.t('Native local LLM: unavailable. No JNI or Android APK is used on NEXT.', '本地 LLM 不可用。NEXT 不使用 JNI 或 Android APK。')).fontColor(this.palette().foreground).fontSize(13)
      Button(this.t('Done', '完成')).onClick(() => { this.settings = false; })
    }.padding(12).width('100%').alignItems(HorizontalAlign.Start)
  }
  @Builder private HeroMenu() {
    Row({ space: 8 }) {
      Button() { Column({ space: 6 }) { Image($r('app.media.chat')).width(30).height(30); Text(this.t('New chat', '新对话')).fontColor(this.palette().foreground) } }
        .layoutWeight(1).height(108).backgroundColor(this.palette().surface).borderRadius(18).onClick(() => { this.stop(); this.messages = []; this.usage = ''; })
      Button() { Column({ space: 6 }) { Image($r('app.media.models')).width(30).height(30); Text(this.t('Models', '模型')).fontColor(this.palette().foreground) } }
        .layoutWeight(1).height(108).backgroundColor(this.palette().surface).borderRadius(18).onClick(() => { this.settings = true; })
      Button() { Column({ space: 6 }) { Image($r('app.media.settings')).width(30).height(30); Text(this.t('Style', '外观')).fontColor(this.palette().foreground) } }
        .layoutWeight(1).height(108).backgroundColor(this.palette().surface).borderRadius(18).onClick(() => { this.settings = true; })
    }.width('100%')
  }
  private replyParagraphs(content: string): string[] {
    const paragraphs = content.split(/\n\s*\n/);
    return paragraphs.length <= 800 ? paragraphs : [content];
  }
  // Native selectable reader: paragraph and heading spacing, full raw code retained. Never loads remote HTML/images.
  @Builder private ReplyReader(content: string) {
    Column({ space: 14 }) {
      ForEach(this.replyParagraphs(content), (paragraph: string, index: number) => {
        if (/^#{1,3}\s/.test(paragraph)) {
          Text(paragraph.replace(/^#{1,3}\s/, '')).fontSize(24 * this.fontScale).fontWeight(FontWeight.Bold).fontColor(this.palette().foreground).copyOption(CopyOptions.LocalDevice)
        } else {
          Text(paragraph).fontSize(17 * this.fontScale).lineHeight(27 * this.fontScale).fontColor(this.palette().foreground).copyOption(CopyOptions.LocalDevice)
        }
      }, (_: string, index: number) => index.toString())
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
  build() {
    Column() {
      Row() {
        Column() {
          Text('Meshlit').fontSize(18).fontWeight(FontWeight.Bold).fontColor(this.palette().foreground)
          Text(this.t('HarmonyOS NEXT · Experimental client', 'HarmonyOS NEXT · 实验性客户端')).fontSize(12).fontColor(this.palette().foreground)
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        Button(this.t('New', '新对话')).onClick(() => { this.stop(); this.messages = []; this.usage = ''; })
        Button(this.t('Settings', '设置')).onClick(() => { this.settings = !this.settings; })
      }.padding({ left: 12, right: 12, top: 4, bottom: 4 }).height(52).width('100%')
      if (this.error) { Text(this.t('Action failed. Check settings, TLS, token, context size or preferences storage.', '操作失败。请检查设置、TLS、令牌、上下文长度或设置存储。')).fontColor('#F87171').padding(16) }
      Scroll() {
        if (this.settings) { this.SettingsPanel() }
        else {
          Column({ space: 16 }) {
            if (this.messages.length === 0) {
              Text('✦').fontSize(50).fontColor(this.palette().accent)
              Text(this.t('What should we work on?', '我们开始做什么？')).fontSize(30).fontColor(this.palette().foreground)
              this.HeroMenu()
              Text(this.t('Select a trusted host in Settings. Local inference is not integrated yet.', '请在设置中选择可信主机。本地推理尚未集成。')).fontColor(this.palette().foreground)
            }
            ForEach(this.messages, (turn: Turn, index: number) => {
              if (turn.role === 'user') {
                Text(turn.content).fontSize(17 * this.fontScale).fontColor(this.palette().foreground).padding(12).backgroundColor(this.palette().surface).borderRadius(22).copyOption(CopyOptions.LocalDevice)
              } else { this.ReplyReader(turn.content) }
            }, (_: Turn, index: number) => index.toString())
            if (this.busy) { Text(this.t('Working…', '正在处理…')).fontColor(this.palette().foreground) }
          }.padding(12).width('100%').alignItems(HorizontalAlign.Start)
        }
      }.layoutWeight(1).width('100%')
      if (!this.settings) {
        Row({ space: 10 }) {
          TextInput({ text: this.prompt, placeholder: this.t('Ask Meshlit', '向 Meshlit 提问') }).layoutWeight(1).onChange((value: string) => { this.prompt = value.slice(0, 131072); })
          Button(this.busy ? this.t('Stop', '停止') : this.t('Send ↑', '发送 ↑')).enabled(this.busy || (this.network && this.models.length > 0 && !!this.prompt.trim())).onClick(() => { if (this.busy) this.stop(); else this.send(); })
        }.padding(12).backgroundColor(this.palette().surface).borderRadius(26).margin({ left: 12, right: 12 })
        Text(this.usage || this.t('Reported tokens / speed: unknown', '报告的令牌 / 速度：未知')).fontColor(this.palette().foreground).fontSize(12).padding(12)
      }
    }.width('100%').height('100%').backgroundColor(this.palette().background)
  }
}
