package com.lrj.risk.fraud.gateway.decision.adapter.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.lrj.risk.fraud.gateway.decision.application.port.out.AccountTokenizationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deterministic HMAC token permits equality search without persisting the clear account number. */
@Component
public class HmacAccountTokenizer implements AccountTokenizationPort {

    private final byte[] key;

    public HmacAccountTokenizer(@Value("${risk.security.pii-hmac-key:local-development-only}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String tokenize(String accountNo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(accountNo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
