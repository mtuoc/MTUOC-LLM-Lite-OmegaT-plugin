package org.omegat.plugins.machinetranslators;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.omegat.core.Core;
import org.omegat.core.machinetranslators.BaseTranslate;
import org.omegat.util.Language;
import org.omegat.util.Preferences;

public class MTUOCLLMLite extends BaseTranslate {

    private static final String PREF_ALLOW = "allow_mtuoc_llm_lite";
    private static final String PREF_URL = "mtuoc_llm_lite_api_url";
    private static final String PREF_API_KEY = "mtuoc_llm_lite_api_key";
    private static final String PREF_MODEL = "mtuoc_llm_lite_model";
    private static final String PREF_TEMPERATURE = "mtuoc_llm_lite_temperature";
    private static final String PREF_REASONING = "mtuoc_llm_lite_reasoning_effort";
    private static final String PREF_SYSTEM_PROMPT = "mtuoc_llm_lite_system_prompt";
    private static final String PREF_USER_PROMPT = "mtuoc_llm_lite_user_prompt";

    private static final String DEFAULT_URL = "http://localhost:8004/v1";
    private static final String DEFAULT_API_KEY = "EMPTY";
    private static final String DEFAULT_MODEL = "qwen3.8-27b";
    private static final double DEFAULT_TEMPERATURE = 0.1;
    private static final String DEFAULT_REASONING = "none";
    private static final String[] REASONING_OPTIONS = {"none", "default", "low", "medium", "high"};

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a professional translator. Translate the user's text accurately, naturally, " +
            "and fluently. Preserve all inline formatting tags (such as <g0>, </g0>, <x1/>) in their " +
            "exact logical positions. Output ONLY the translated text without any explanations, " +
            "notes, quotes, or thinking blocks.";
    private static final String DEFAULT_USER_PROMPT =
            "Translate from {source_lang} to {target_lang}:\n\n{text}";

    private final HttpClient httpClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MTUOCLLMLite() {
        super();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static void loadPlugins() {
        Core.registerMachineTranslationClass(MTUOCLLMLite.class);
    }

    public static void unloadPlugins() {
    }

    @Override
    protected String getPreferenceName() {
        return PREF_ALLOW;
    }

    @Override
    public String getName() {
        return "MTUOC-LLM-Lite";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String baseUrl = normalizeUrl(Preferences.getPreferenceDefault(PREF_URL, DEFAULT_URL));
        String apiKey = Preferences.getPreferenceDefault(PREF_API_KEY, DEFAULT_API_KEY);
        String model = Preferences.getPreferenceDefault(PREF_MODEL, DEFAULT_MODEL);
        double temperature = parseTemperature(Preferences.getPreferenceDefault(PREF_TEMPERATURE, String.valueOf(DEFAULT_TEMPERATURE)));
        String reasoningEffort = Preferences.getPreferenceDefault(PREF_REASONING, DEFAULT_REASONING);
        String systemPrompt = Preferences.getPreferenceDefault(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        String userTemplate = Preferences.getPreferenceDefault(PREF_USER_PROMPT, DEFAULT_USER_PROMPT);

        String cacheKey = model + "|" + temperature + "|" + reasoningEffort + "|"
                + sLang.getLanguage() + "|" + tLang.getLanguage() + "|" + text;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String sourceLangName = sLang.getDisplayName() + " (" + sLang.getLanguage() + ")";
        String targetLangName = tLang.getDisplayName() + " (" + tLang.getLanguage() + ")";

        String userPrompt = userTemplate
                .replace("{source_lang}", sourceLangName)
                .replace("{target_lang}", targetLangName)
                .replace("{text}", text);

        String translated = sendChatCompletion(
                baseUrl, apiKey, model, temperature, reasoningEffort, systemPrompt, userPrompt, 90
        );
        if (!translated.isEmpty()) {
            cache.put(cacheKey, translated);
        }
        return translated;
    }

    private String sendChatCompletion(String baseUrl, String apiKey, String model,
                                      double temperature, String reasoningEffort,
                                      String systemPrompt, String userPrompt, int timeoutSeconds) throws Exception {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{")
                .append("\"model\":\"").append(escapeJson(model)).append("\",")
                .append(String.format(Locale.US, "\"temperature\":%.2f,", temperature));

        if (reasoningEffort != null && !reasoningEffort.trim().isEmpty()
                && !"default".equalsIgnoreCase(reasoningEffort.trim())) {
            jsonBuilder.append("\"reasoning_effort\":\"").append(escapeJson(reasoningEffort.trim())).append("\",");
        }

        jsonBuilder.append("\"messages\":[")
                .append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},")
                .append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userPrompt)).append("\"}")
                .append("]}");

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString(), StandardCharsets.UTF_8));

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpResponse<String> response = httpClient.send(
                reqBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Exception("Server HTTP " + response.statusCode() + ": " + response.body());
        }

        String translated = extractAssistantContent(response.body());
        return cleanModelOutput(translated);
    }

    @Override
    public void showConfigurationUI(Window parent) {
        String currentUrl = Preferences.getPreferenceDefault(PREF_URL, DEFAULT_URL);
        String currentKey = Preferences.getPreferenceDefault(PREF_API_KEY, DEFAULT_API_KEY);
        String currentModel = Preferences.getPreferenceDefault(PREF_MODEL, DEFAULT_MODEL);
        double currentTemp = parseTemperature(Preferences.getPreferenceDefault(PREF_TEMPERATURE, String.valueOf(DEFAULT_TEMPERATURE)));
        String currentReasoning = Preferences.getPreferenceDefault(PREF_REASONING, DEFAULT_REASONING);
        String currentSysPrompt = Preferences.getPreferenceDefault(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        String currentUserPrompt = Preferences.getPreferenceDefault(PREF_USER_PROMPT, DEFAULT_USER_PROMPT);

        JDialog dialog = new JDialog(parent, "MTUOC-LLM-Lite Configuration", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // 1. Base URL
        JTextField urlField = new JTextField(currentUrl, 34);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Server Base URL (/v1):"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        panel.add(urlField, gbc);

        // 2. API Key
        JPasswordField keyField = new JPasswordField(currentKey, 34);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("API Key (Bearer):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        panel.add(keyField, gbc);

        // 3. Model Combo + Refresh + Test Connection
        JComboBox<String> modelCombo = new JComboBox<>(new String[]{currentModel});
        modelCombo.setEditable(true);
        modelCombo.setSelectedItem(currentModel);

        // 4. Temperature & Reasoning Effort controls (defined before Test button so Test uses them)
        JSpinner tempSpinner = new JSpinner(new SpinnerNumberModel(currentTemp, 0.0, 2.0, 0.05));
        JComboBox<String> reasoningCombo = new JComboBox<>(REASONING_OPTIONS);
        reasoningCombo.setSelectedItem(currentReasoning);

        JButton refreshBtn = new JButton("Refresh Models");
        refreshBtn.addActionListener(e -> {
            refreshBtn.setEnabled(false);
            refreshBtn.setText("Loading...");
            String url = normalizeUrl(urlField.getText());
            String key = new String(keyField.getPassword()).trim();

            new Thread(() -> {
                try {
                    List<String> models = fetchAvailableModels(url, key);
                    SwingUtilities.invokeLater(() -> {
                        Object selected = modelCombo.getEditor().getItem();
                        modelCombo.removeAllItems();
                        for (String m : models) {
                            modelCombo.addItem(m);
                        }
                        if (selected != null && !selected.toString().isEmpty()) {
                            modelCombo.setSelectedItem(selected.toString());
                        }
                        refreshBtn.setText("Refresh Models");
                        refreshBtn.setEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        refreshBtn.setText("Refresh Models");
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog,
                                "Failed to fetch models:\n" + ex.getMessage(),
                                "MTUOC-LLM-Lite Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });

        JButton testBtn = new JButton("Test Connection");
        testBtn.addActionListener(e -> {
            testBtn.setEnabled(false);
            testBtn.setText("Testing...");
            String url = normalizeUrl(urlField.getText());
            String key = new String(keyField.getPassword()).trim();
            Object item = modelCombo.getEditor().getItem();
            String selectedModel = (item != null && !item.toString().trim().isEmpty())
                    ? item.toString().trim() : DEFAULT_MODEL;
            double selectedTemp = ((Number) tempSpinner.getValue()).doubleValue();
            String selectedReasoning = (String) reasoningCombo.getSelectedItem();

            new Thread(() -> {
                try {
                    long start = System.currentTimeMillis();
                    String reply = sendChatCompletion(
                            url,
                            key,
                            selectedModel,
                            selectedTemp,
                            selectedReasoning,
                            "You are a connection test assistant.",
                            "Reply with exactly one word: OK",
                            30
                    );
                    long elapsedMs = System.currentTimeMillis() - start;
                    SwingUtilities.invokeLater(() -> {
                        testBtn.setText("Test Connection");
                        testBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog,
                                "Connection successful!\n"
                                + "Model: " + selectedModel + "\n"
                                + "Reasoning: " + selectedReasoning + " | Temp: " + selectedTemp + "\n"
                                + "Response: " + reply + "\n"
                                + "Latency: " + elapsedMs + " ms",
                                "MTUOC-LLM-Lite Connection Test", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        testBtn.setText("Test Connection");
                        testBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog,
                                "Connection failed:\n" + ex.getMessage(),
                                "MTUOC-LLM-Lite Connection Test", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 1.0;
        panel.add(modelCombo, gbc);
        gbc.gridx = 2; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(refreshBtn, gbc);
        gbc.gridx = 3; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(testBtn, gbc);

        // Row 3: Temperature and Reasoning Effort
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Temperature:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.5;
        panel.add(tempSpinner, gbc);
        gbc.gridx = 2; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Reasoning Effort:"), gbc);
        gbc.gridx = 3; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.5;
        panel.add(reasoningCombo, gbc);

        // 5. System Prompt
        JTextArea sysArea = new JTextArea(currentSysPrompt, 4, 42);
        sysArea.setLineWrap(true);
        sysArea.setWrapStyleWord(true);
        JScrollPane sysScroll = new JScrollPane(sysArea);
        sysScroll.setPreferredSize(new Dimension(520, 90));

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4; gbc.weightx = 1.0;
        panel.add(new JLabel("System Prompt (translator role and rules):"), gbc);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 0.5;
        panel.add(sysScroll, gbc);

        // 6. User Prompt
        JTextArea userArea = new JTextArea(currentUserPrompt, 4, 42);
        userArea.setLineWrap(true);
        userArea.setWrapStyleWord(true);
        JScrollPane userScroll = new JScrollPane(userArea);
        userScroll.setPreferredSize(new Dimension(520, 90));

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weighty = 0.0;
        panel.add(new JLabel("User Prompt (placeholders: {source_lang}, {target_lang}, {text}):"), gbc);
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 0.5;
        panel.add(userScroll, gbc);

        // OK / Cancel buttons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> {
            String savedUrl = normalizeUrl(urlField.getText());
            String savedKey = new String(keyField.getPassword()).trim();
            Object item = modelCombo.getEditor().getItem();
            String savedModel = (item != null) ? item.toString().trim() : DEFAULT_MODEL;
            double savedTemp = ((Number) tempSpinner.getValue()).doubleValue();
            String savedReasoning = (String) reasoningCombo.getSelectedItem();
            String savedSys = sysArea.getText();
            String savedUser = userArea.getText();

            Preferences.setPreference(PREF_URL, savedUrl.isEmpty() ? DEFAULT_URL : savedUrl);
            Preferences.setPreference(PREF_API_KEY, savedKey);
            Preferences.setPreference(PREF_MODEL, savedModel.isEmpty() ? DEFAULT_MODEL : savedModel);
            Preferences.setPreference(PREF_TEMPERATURE, String.format(Locale.US, "%.2f", savedTemp));
            Preferences.setPreference(PREF_REASONING, savedReasoning != null ? savedReasoning : DEFAULT_REASONING);
            Preferences.setPreference(PREF_SYSTEM_PROMPT, savedSys);
            Preferences.setPreference(PREF_USER_PROMPT, savedUser);

            cache.clear();
            if (Core.getMainWindow() != null && Core.getMainWindow().getApplicationFrame() != null) {
                Core.getMainWindow().getApplicationFrame().repaint();
            }
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        buttonsPanel.add(okBtn);
        buttonsPanel.add(cancelBtn);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonsPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private List<String> fetchAvailableModels(String baseUrl, String apiKey) throws Exception {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models"))
                .timeout(Duration.ofSeconds(10))
                .GET();

        if (apiKey != null && !apiKey.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(
                reqBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
        }

        List<String> models = new ArrayList<>();
        Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = idPattern.matcher(response.body());
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!models.contains(id)) {
                models.add(id);
            }
        }
        if (models.isEmpty()) {
            throw new Exception("No models found in server response.");
        }
        return models;
    }

    private static double parseTemperature(String val) {
        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_TEMPERATURE;
        }
        try {
            double parsed = Double.parseDouble(val.trim().replace(',', '.'));
            return Math.max(0.0, Math.min(2.0, parsed));
        } catch (NumberFormatException e) {
            return DEFAULT_TEMPERATURE;
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) return DEFAULT_URL;
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String cleanModelOutput(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String extractAssistantContent(String json) throws Exception {
        int choicesIdx = json.indexOf("\"choices\"");
        if (choicesIdx == -1) {
            throw new Exception("Unexpected server response: " + json);
        }
        int contentKeyIdx = json.indexOf("\"content\"", choicesIdx);
        if (contentKeyIdx == -1) {
            throw new Exception("Missing 'content' field in response.");
        }
        int colonIdx = json.indexOf(':', contentKeyIdx + 9);
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) {
            throw new Exception("Invalid JSON format in 'content' field.");
        }

        StringBuilder result = new StringBuilder();
        boolean escape = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case '"':  result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/':  result.append('/'); break;
                    case 'b':  result.append('\b'); break;
                    case 'f':  result.append('\f'); break;
                    case 'n':  result.append('\n'); break;
                    case 'r':  result.append('\r'); break;
                    case 't':  result.append('\t'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            result.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        break;
                    default: result.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }
        throw new Exception("Unterminated JSON string in 'content' field.");
    }
}
