export interface RuntimeCapabilities { localInference: boolean; detail: string; }
export const capabilities: () => RuntimeCapabilities;
