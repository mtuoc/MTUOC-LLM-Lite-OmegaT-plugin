# MTUOC-LLM-Lite — OmegaT Machine Translation Plugin

🌐 **Idioma / Language:** [Català](#catala) | [English](#english) | [Español](#espanol)

---

## <a name="catala"></a>Català

Connector de traducció automàtica per a **OmegaT 6.x** que permet connectar-se a qualsevol servidor compatible amb l'API d'OpenAI, com ara un proxy **LiteLLM**, **Ollama** o **vLLM**.

### Característiques
- Detecció dinàmica de models disponibles mitjançant l'endpoint `/v1/models`.
- Botó integrat **Test Connection** per verificar la connexió i la latència.
- Control configurable de la **Temperature** (`0.0` – `2.0`) i del nivell de raonament (**Reasoning Effort**: `none`, `default`, `low`, `medium`, `high`) per accelerar els models amb capacitat de raonament (com Qwen 3 o DeepSeek).
- Plantilles personalitzables per al *System Prompt* i el *User Prompt* amb les variables `{source_lang}`, `{target_lang}` i `{text}`.
- Eliminació automàtica dels blocs `<think>...</think>` si el model en genera.

### Instal·lació

1. Descarregueu l'última versió del fitxer `.jar` del connector (per exemple, `MTUOC-LLM-Lite-20261127.jar`) des de la secció **[Releases](../../releases)** d'aquest repositori.
2. Copieu el fitxer `.jar` a la carpeta `plugins` de la vostra instal·lació d'OmegaT:
   - **macOS:** `~/Library/Preferences/OmegaT/plugins/` *(o bé `~/.omegat/plugins/`)*
   - **Linux:** `~/.omegat/plugins/`
   - **Windows:** `%APPDATA%\OmegaT\plugins\`
3. Reinicieu OmegaT perquè carregui el connector.

### Configuració a OmegaT

1. Obriu OmegaT i aneu a **Preferències → Traducció automàtica** (*Preferences → Machine Translation*).
2. Activeu la casella **MTUOC-LLM-Lite** i feu clic al botó **Configura** (*Configure*).
3. Ompliu els paràmetres del servidor:
   - **Server Base URL (/v1):** Adreça del servidor acabada en `/v1` (per exemple, `http://localhost:8004/v1`).
   - **API Key (Bearer):** Clau d'accés configurada al servidor (o `EMPTY` si no en requereix).
   - **Model:** Premeu **Refresh Models** per obtenir la llista de models del servidor i seleccioneu el que vulgueu utilitzar.
   - **Temperature:** Ajusteu la creativitat/aleatorietat del model (es recomana `0.10` per a traducció).
   - **Reasoning Effort:** Seleccioneu `none` per desactivar el raonament intern en models compatibles (com `qwen3.8`) i obtenir respostes gairebé instantànies, o trieu el nivell desitjat (`low`, `medium`, `high`, `default`).
   - **System Prompt / User Prompt:** Podeu deixar els valors per defecte o adaptar les instruccions de traducció.
4. Premeu **Test Connection** per comprovar que el servidor respon correctament i feu clic a **OK** per desar els canvis.

![Finestra de configuració de MTUOC-LLM-Lite](docs/screenshot-config.png)

### Compilació des del codi font

Si voleu compilar el connector vosaltres mateixos a partir del codi font, executeu l'ordre següent al directori arrel del projecte:

```bash
./gradlew jar
