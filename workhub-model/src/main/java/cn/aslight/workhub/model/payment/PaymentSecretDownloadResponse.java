package cn.aslight.workhub.model.payment;

/**
 * 秘钥文件下载响应。
 */
public record PaymentSecretDownloadResponse(String fileName,
                                            String contentType,
                                            byte[] content) {
}
