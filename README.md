# OmegaT LiteLLM Plugin (Lite)

Machine translation plugin for OmegaT 6.x that connects to any OpenAI-compatible LiteLLM proxy endpoint.

## Features
- Dynamic model discovery via `/v1/models`
- Built-in **Test Connection** button
- Customizable `system` and `user` prompt templates (`{source_lang}`, `{target_lang}`, `{text}`)
- Automatic stripping of `<think>...</think>` blocks from reasoning models

## Build
Run `gradle jar` in the project root directory. The compiled plugin will be generated at `build/libs/omegat-litellm-plugin-1.0.1.jar`.

## Installation
Copy the `.jar` file to your OmegaT `plugins` directory:
- **macOS:** `~/Library/Preferences/OmegaT/plugins/`
- **Linux:** `~/.omegat/plugins/`
- **Windows:** `%APPDATA%\OmegaT\plugins\`
