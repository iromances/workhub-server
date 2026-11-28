package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.service.runtime.HostTools;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 复用结果必须对应相同输入及已核实的干净代码版本；不读取凭据、不执行 Git 写操作。 */
final class AiAnalysisFingerprint {
    static String create(AiGatewayRequest request, Long providerId, String model, String reasoning, String prompt, String schema) {
        var parts = new ArrayList<String>(List.of("intake-result-v1", request.useCaseCode(), String.valueOf(providerId), model,
                String.valueOf(reasoning), prompt, String.valueOf(schema), String.valueOf(request.workingDirectory())));
        for (String directory : request.allowedDirectories().stream().sorted().toList()) {
            parts.add(directory);
            String status = git(directory, "status", "--porcelain=v1", "--untracked-files=normal");
            String head = git(directory, "rev-parse", "HEAD");
            if (status == null || !status.isBlank() || head == null || !head.strip().matches("[a-f0-9]{40,64}")) {
                // 仍保存本次原文，但不能把未知/脏代码状态当作可恢复的相同输入。
                parts.add(UUID.randomUUID().toString());
            } else parts.add(head.strip());
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static String git(String directory, String... arguments) {
        Process process = null;
        try {
            var command = new ArrayList<>(List.of(HostTools.git(), "--no-optional-locks", "-C", directory));
            command.addAll(List.of(arguments));
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process observed = process;
            var bytes = new ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> {
                try (var input = observed.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    for (int count; (count = input.read(chunk)) >= 0;) {
                        bytes.write(chunk, 0, Math.min(count, 65537 - bytes.size()));
                    }
                }
                catch (java.io.IOException ignored) { }
            });
            if (!process.waitFor(5, TimeUnit.SECONDS)) return null;
            reader.join(1000);
            return process.exitValue() == 0 && !reader.isAlive() && bytes.size() <= 65536 ? bytes.toString(StandardCharsets.UTF_8) : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("代码版本核对被中断", ex);
        } catch (java.io.IOException | IllegalArgumentException ex) { return null; }
        finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
