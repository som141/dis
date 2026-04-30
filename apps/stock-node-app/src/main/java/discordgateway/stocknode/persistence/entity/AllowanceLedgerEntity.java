package discordgateway.stocknode.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "allowance_ledger")
public class AllowanceLedgerEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private StockAccountEntity account;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "allowance_type", nullable = false, length = 32)
    private String allowanceType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AllowanceLedgerEntity() {
    }

    private AllowanceLedgerEntity(
            StockAccountEntity account,
            BigDecimal amount,
            String allowanceType,
            Instant occurredAt
    ) {
        this.account = Objects.requireNonNull(account, "account");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.allowanceType = Objects.requireNonNull(allowanceType, "allowanceType");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AllowanceLedgerEntity create(
            StockAccountEntity account,
            BigDecimal amount,
            String allowanceType,
            Instant occurredAt
    ) {
        return new AllowanceLedgerEntity(account, amount, allowanceType, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public StockAccountEntity getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAllowanceType() {
        return allowanceType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
