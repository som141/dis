package discordgateway.stocknode.application;

import java.util.List;

public record StockListView(
        String market,
        List<StockListItemView> items
) {
}
