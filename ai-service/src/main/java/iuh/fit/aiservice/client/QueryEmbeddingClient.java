package iuh.fit.aiservice.client;

import java.util.List;

public interface QueryEmbeddingClient {
    List<Double> embed(String text);
}
