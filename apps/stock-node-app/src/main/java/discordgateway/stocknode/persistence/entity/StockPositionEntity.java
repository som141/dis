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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(
        name = "stock_position",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_position_account_symbol",
                columnNames = {"account_id", "symbol"}
        )
)
public class StockPositionEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private StockAccountEntity account;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageCost;

    protected StockPositionEntity() {
    }

    private StockPositionEntity(
            StockAccountEntity account,
            String symbol,
            BigDecimal quantity,
            BigDecimal averageCost
    ) {
        this.account = Objects.requireNonNull(account, "account");
        this.symbol = normalizeSymbol(symbol);
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.averageCost = Objects.requireNonNull(averageCost, "averageCost");
    }

    public static StockPositionEntity create(StockAccountEntity account, String symbol) {
        return new StockPositionEntity(account, symbol, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static String normalizeSymbol(String symbol) {
        return Objects.requireNonNull(symbol, "symbol").trim().toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public StockAccountEntity getAccount() {
        return account;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }
}
