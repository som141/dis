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
import java.math.RoundingMode;
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

    public void applyBuy(BigDecimal buyQuantity, BigDecimal unitPrice) {
        BigDecimal normalizedBuyQuantity = scaleQuantity(buyQuantity);
        BigDecimal normalizedUnitPrice = scaleCash(unitPrice);
        BigDecimal totalCost = quantity.multiply(averageCost).add(normalizedBuyQuantity.multiply(normalizedUnitPrice));
        BigDecimal totalQuantity = quantity.add(normalizedBuyQuantity);
        quantity = scaleQuantity(totalQuantity);
        averageCost = totalQuantity.signum() == 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : totalCost.divide(totalQuantity, 4, RoundingMode.HALF_UP);
    }

    public void applySell(BigDecimal sellQuantity) {
        quantity = scaleQuantity(quantity.subtract(scaleQuantity(sellQuantity)));
        if (quantity.signum() == 0) {
            averageCost = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
    }

    public boolean hasEnoughQuantity(BigDecimal sellQuantity) {
        return quantity.compareTo(scaleQuantity(sellQuantity)) >= 0;
    }

    public boolean isEmpty() {
        return quantity.signum() == 0;
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

    private BigDecimal scaleQuantity(BigDecimal value) {
        return Objects.requireNonNull(value, "value").setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleCash(BigDecimal value) {
        return Objects.requireNonNull(value, "value").setScale(4, RoundingMode.HALF_UP);
    }
}
