# Inference settings in Studio 2.0.46 preview

In the packaged desktop client, open Engine and select Low memory, Balanced, or Long context. The profile proposes generation and prompt worker counts using the processors available to Studio. Advanced settings expose context length, both thread counts, batch sizes, key-cache precision and CPU backend. Choose the idle timeout separately. Apply once to save all edits, unload the current local model and end the current chat. Discard returns to the saved values. Loading a model still checks its context limit and estimated memory requirement.

The Code OSS companion has a separate LLM settings page. Quick reply stages a 192-token reply limit and two prior turns. Balanced stages 512 tokens and five turns. Extended reply stages 1,024 tokens and eight turns. Edit these values and choose Save settings. Actual generation length depends on the selected model.

The CI MSI, DEB and DMG are host-client previews. To chat on a computer with an existing local Ollama or LM Studio server, choose its preset under Models → Remote host, review the loopback address, allow the connection and select a model. These three preview installers omit GGUF weights and the bundled inference server. Engine profiles apply to a packaged Meshlit inference server when present. External servers manage their own hardware and memory settings.
