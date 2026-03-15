package tsd.beye.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import tsd.beye.Bullseye;

public class SignatureService {
    private static final String SIGNATURE_TEXT = "DATJR";
    private static final byte[] SIGNATURE_BYTES = SIGNATURE_TEXT.getBytes(StandardCharsets.US_ASCII);
    private static final String SIGNATURE_HEX = HexFormat.of().withUpperCase().formatHex(SIGNATURE_BYTES);
    private static final String SIGNATURE_DECIMAL = new BigInteger(1, SIGNATURE_BYTES).toString();
    private static final String SIGNATURE_BYTE_DECIMAL = buildByteDecimalSignature();

    private final Bullseye plugin;

    private boolean enabled;
    private boolean stopOnFailure;
    private String configuredValue;

    public SignatureService(Bullseye plugin) {
        this.plugin = plugin;
    }

    public void reloadSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("signature-check");
        if (section == null) {
            enabled = false;
            stopOnFailure = false;
            configuredValue = "";
            return;
        }

        enabled = section.getBoolean("enabled", false);
        stopOnFailure = section.getBoolean("stop-on-failure", true);
        configuredValue = section.getString("value", "");
    }

    public boolean validateConfiguredSignature() {
        if (!enabled) {
            return true;
        }

        if (configuredValue == null || configuredValue.isBlank()) {
            plugin.getLogger().warning("Signature check is enabled but no value is configured.");
            return false;
        }

        boolean valid = matches(configuredValue);
        if (valid) {
            plugin.getLogger().info("DATJR signature check passed.");
        } else {
            plugin.getLogger().severe(
                "DATJR signature check failed. Accepted forms: hex=" + SIGNATURE_HEX
                    + ", decimal=" + SIGNATURE_DECIMAL
                    + ", text=" + SIGNATURE_TEXT
            );
        }

        return valid;
    }

    public boolean matches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }

        String trimmed = candidate.trim();
        if (trimmed.equalsIgnoreCase(SIGNATURE_TEXT)) {
            return true;
        }

        String normalizedHex = trimmed.replaceFirst("^(?i)0x", "").replaceAll("[^a-fA-F0-9]", "").toUpperCase(Locale.ROOT);
        if (SIGNATURE_HEX.equals(normalizedHex)) {
            return true;
        }

        String digits = trimmed.replaceAll("[^0-9]", "");
        if (SIGNATURE_DECIMAL.equals(digits) || SIGNATURE_BYTE_DECIMAL.equals(digits)) {
            return true;
        }

        if (trimmed.matches("\\d+(?:\\s*[,\\-:]\\s*\\d+)+")) {
            String[] parts = trimmed.split("\\s*[,\\-:]\\s*");
            if (parts.length == SIGNATURE_BYTES.length) {
                for (int i = 0; i < parts.length; i++) {
                    int parsed;
                    try {
                        parsed = Integer.parseInt(parts[i]);
                    } catch (NumberFormatException ex) {
                        return false;
                    }

                    if (parsed != Byte.toUnsignedInt(SIGNATURE_BYTES[i])) {
                        return false;
                    }
                }
                return true;
            }
        }

        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isStopOnFailure() {
        return stopOnFailure;
    }

    public String getSignatureText() {
        return SIGNATURE_TEXT;
    }

    public String getSignatureHex() {
        return SIGNATURE_HEX;
    }

    public String getSignatureDecimal() {
        return SIGNATURE_DECIMAL;
    }

    private static String buildByteDecimalSignature() {
        StringBuilder out = new StringBuilder();
        for (byte value : SIGNATURE_BYTES) {
            out.append(Byte.toUnsignedInt(value));
        }
        return out.toString();
    }
}
