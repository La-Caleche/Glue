package fr.lacaleche.glue.web.internal.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded, interruptible requests. Completion includes the body, so the request timeout bounds it too. */
final class BundleHttp implements AutoCloseable {

    private final HttpClient client;

    BundleHttp(HttpClient client) {
        this.client = client;
    }

    byte[] get(URI uri, int maximum) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2))
                .header("Accept-Encoding", "identity").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = this.client.sendAsync(request, info -> new LimitedBody(maximum));
        try {
            HttpResponse<byte[]> response = pending.get(2, TimeUnit.MINUTES);
            if (response.statusCode() != 200) throw new IOException("Bundle request returned HTTP " + response.statusCode());
            return response.body();
        } catch (TimeoutException exception) {
            throw new IOException("Bundle request timed out", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Bundle request failed", exception.getCause());
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    @Override
    public void close() {
        this.client.shutdownNow();
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;
        private Flow.Subscription subscription;
        private long received;

        private LimitedBody(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return this.delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            this.delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) this.received += buffer.remaining();
            if (this.received > this.maximum) {
                this.subscription.cancel();
                this.delegate.onError(new IOException("Bundle response exceeds its size limit"));
            } else {
                this.delegate.onNext(buffers);
            }
        }

        @Override
        public void onError(Throwable error) {
            this.delegate.onError(error);
        }

        @Override
        public void onComplete() {
            this.delegate.onComplete();
        }
    }
}
