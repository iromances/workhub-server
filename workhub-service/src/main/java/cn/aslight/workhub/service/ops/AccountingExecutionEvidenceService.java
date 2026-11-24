package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingExecutionEvidenceMapper;
import cn.aslight.workhub.model.ops.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

@Service
public class AccountingExecutionEvidenceService {
    public static final ZoneId RECORD_ZONE = ZoneId.of("Asia/Shanghai");
    private final AccountingExecutionEvidenceMapper mapper;
    private final ObjectMapper json;
    public AccountingExecutionEvidenceService(AccountingExecutionEvidenceMapper mapper, ObjectMapper json) {
        this.mapper = mapper; this.json = json;
    }

    public Verified read(AccountingRunEntity run) {
        var saved = mapper.find(run.id());
        if (saved == null) return null;
        if (!hash(saved.snapshotJson()).equals(saved.contentHash())) throw invalid();
        var value = json.readValue(saved.snapshotJson(), AccountingExecutionSnapshot.class);
        var config = value.configuration();
        if (value.schemaVersion() != 1 || !run.id().equals(value.runId()) || !run.id().equals(saved.runId())
                || !run.configId().equals(config.configId()) || !run.startTime().equals(value.startTime()) || !run.endTime().equals(value.endTime())
                || !saved.businessLineCode().equals(config.businessLineCode()) || !saved.environmentCode().equals(config.environmentCode())
                || !saved.systemName().equals(config.systemName()) || !saved.displayName().equals(config.displayName())
                || !hash(json.writeValueAsString(config)).equals(value.configurationHash())
                || !hash(json.writeValueAsString(value.rules())).equals(value.rulesHash())) throw invalid();
        ZoneId.of(value.recordTimeZone());
        return new Verified(value, saved.contentHash(), saved.capturedAt());
    }

    public ObjectNode describe(AccountingRunEntity run) {
        var value = (ObjectNode) json.valueToTree(run);
        var saved = read(run);
        var evidence = json.createObjectNode().put("scopeVerified", saved != null);
        if (saved == null) evidence.put("state", "UNRECORDED");
        else {
            evidence.put("state", "RECORDED").put("contentHash", saved.contentHash());
            evidence.set("capturedAt", json.valueToTree(saved.capturedAt()));
            evidence.set("snapshot", json.valueToTree(saved.snapshot()));
        }
        value.set("executionEvidence", evidence);
        return value;
    }

    public static boolean scopeVerified(JsonNode run) { return run.path("executionEvidence").path("scopeVerified").asBoolean(); }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("账务执行凭据与运行记录不一致"); }
    public record Verified(AccountingExecutionSnapshot snapshot, String contentHash, LocalDateTime capturedAt) { }
}
