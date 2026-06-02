package iuh.fit.aiservice.recommendation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_view_logs", indexes = {
        @Index(name = "idx_view_logs_customer", columnList = "customer_id"),
    @Index(name = "idx_view_logs_viewed_at", columnList = "viewed_at"),
    @Index(name = "idx_view_logs_event_type", columnList = "event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductViewLogEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductViewSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private BehaviorEventType eventType;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;
}
