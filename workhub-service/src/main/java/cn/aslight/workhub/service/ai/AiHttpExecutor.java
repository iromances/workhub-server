package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** 分别落实连接、响应体读取空闲和整个调用三个超时维度。 */
final class AiHttpExecutor {
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "workhub-ai-http-read-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private AiHttpExecutor() {}

    static HttpResponse<String> send(HttpRequest request, AiProviderConfigEntity provider,
                                     Integer callTimeoutSeconds) throws Exception {
        int connect = positive(provider.getConnectTimeoutSeconds(), 30);
        int read = positive(provider.getReadTimeoutSeconds(), 300);
        int call = positive(callTimeoutSeconds, positive(provider.getCallTimeoutSeconds(), 600));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connect)).build();
        HttpRequest timedRequest = HttpRequest.newBuilder(request, (name, value) -> true)
                .timeout(Duration.ofSeconds(call))
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                .build();
        CompletableFuture<HttpResponse<String>> future = client.sendAsync(
                timedRequest,
                responseInfo -> new ReadTimeoutSubscriber(read)
        );
        try {
            return future.get(call, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new HttpTimeoutException("AI 调用超过总超时时间 " + call + " 秒");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            throw ex;
        }
    }

    private static int positive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static final class ReadTimeoutSubscriber implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate =
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private final int timeoutSeconds;
        private volatile Flow.Subscription subscription;
        private volatile ScheduledFuture<?> timeout;

        private ReadTimeoutSubscriber(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            resetTimeout();
        }

        @Override
        public CompletionStage<String> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
            resetTimeout();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            resetTimeout();
            delegate.onNext(item);
            resetTimeout();
        }

        @Override
        public void onError(Throwable throwable) {
            cancelTimeout();
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            cancelTimeout();
            delegate.onComplete();
        }

        private synchronized void resetTimeout() {
            cancelTimeout();
            timeout = TIMEOUTS.schedule(() -> {
                Flow.Subscription current = subscription;
                if (current != null) current.cancel();
                delegate.onError(new HttpTimeoutException("AI 响应体读取超过 " + timeoutSeconds + " 秒无数据"));
            }, timeoutSeconds, TimeUnit.SECONDS);
        }

        private synchronized void cancelTimeout() {
            if (timeout != null) {
                timeout.cancel(false);
                timeout = null;
            }
        }
    }
}
