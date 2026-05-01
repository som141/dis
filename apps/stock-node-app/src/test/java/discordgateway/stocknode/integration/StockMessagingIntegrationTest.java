package discordgateway.stocknode.integration;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stock.messaging.StockMessagingProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "stock.messaging.enabled=true",
        "stock.messaging.listener-enabled=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockMessagingIntegrationTest extends StockNodeMessagingIntegrationTestSupport {

    private static final String RESPONSE_NODE = "gateway-test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private StockMessagingProperties messagingProperties;

    @Autowired
    private QuoteRepository quoteRepository;

    private String resultQueueName;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE account_snapshot, allowance_ledger, trade_ledger, stock_position, stock_account RESTART IDENTITY CASCADE");
        resultQueueName = messagingProperties.commandResultRoutingKey(RESPONSE_NODE);
        Queue queue = QueueBuilder.durable(resultQueueName).build();
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(
                BindingBuilder.bind(queue)
                        .to(new DirectExchange(messagingProperties.getCommandResultExchange(), true, false))
                        .with(resultQueueName)
        );
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(resultQueueName);
            channel.queuePurge(messagingProperties.getCommandQueue());
            return null;
        });
        cacheQuote("AAPL", "200.00");
        cacheQuote("MSFT", "300.00");
    }

    @AfterEach
    void tearDown() {
        if (resultQueueName != null) {
            amqpAdmin.deleteQueue(resultQueueName);
        }
    }

    @Test
    void processesQuoteCommandEndToEnd() {
        StockCommandResultEvent event = sendAndReceive(new StockCommandEnvelope(
                "cmd-quote",
                1,
                Instant.now().toEpochMilli(),
                "gateway",
                new StockCommand.Quote(1001L, 2002L, List.of("AAPL")),
                RESPONSE_NODE
        ));

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("QUOTE");
        assertThat(event.message()).contains("AAPL");
        assertThat(event.message()).contains("200.0000");
        assertThat(event.message()).contains("KST");
    }

    @Test
    void processesMultiQuoteCommandEndToEnd() {
        StockCommandResultEvent event = sendAndReceive(new StockCommandEnvelope(
                "cmd-quote-multi",
                1,
                Instant.now().toEpochMilli(),
                "gateway",
                new StockCommand.Quote(1001L, 2002L, List.of("AAPL", "MSFT")),
                RESPONSE_NODE
        ));

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("QUOTE");
        assertThat(event.message()).contains("AAPL");
        assertThat(event.message()).contains("MSFT");
        assertThat(event.message()).contains("```text");
        assertThat(event.message()).contains("SYMBOL");
        assertThat(event.message()).contains("PRICE");
    }

    @Test
    void processesListCommandEndToEnd() {
        StockCommandResultEvent event = sendAndReceive(new StockCommandEnvelope(
                "cmd-list",
                1,
                Instant.now().toEpochMilli(),
                "gateway",
                new StockCommand.ListQuotes(1001L, 2002L),
                RESPONSE_NODE
        ));

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("LIST");
        assertThat(event.message()).contains("AAPL");
        assertThat(event.message()).contains("NVDA");
        assertThat(event.message()).contains("Finnhub REST API");
        assertThat(event.message()).contains("```text");
    }

    @Test
    void processesBuyAndHistoryCommandsEndToEnd() {
        StockCommandResultEvent buyEvent = sendAndReceive(new StockCommandEnvelope(
                "cmd-buy",
                1,
                Instant.now().toEpochMilli(),
                "gateway",
                new StockCommand.Buy(1001L, 2002L, "AAPL", new BigDecimal("1000.00"), 1),
                RESPONSE_NODE
        ));
        assertThat(buyEvent.success()).isTrue();
        assertThat(buyEvent.resultType()).isEqualTo("BUY");

        StockCommandResultEvent historyEvent = sendAndReceive(new StockCommandEnvelope(
                "cmd-history",
                1,
                Instant.now().toEpochMilli(),
                "gateway",
                new StockCommand.History(1001L, 2002L, 10),
                RESPONSE_NODE
        ));
        assertThat(historyEvent.success()).isTrue();
        assertThat(historyEvent.resultType()).isEqualTo("HISTORY");
        assertThat(historyEvent.message()).contains("AAPL");
        assertThat(historyEvent.message()).contains("200.0000");
    }

    @Test
    void processesRankCommandEndToEnd() {
        StockCommandResultEvent event = sendAndReceive(new StockCommandEnvelope(
                "cmd-rank",
                1,
                Instant.now().toEpochMilli(),
                "gateway",
                new StockCommand.Rank(1001L, 2002L, "day"),
                RESPONSE_NODE
        ));

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("RANK");
        assertThat(event.message()).contains(YearMonth.now(ZoneId.of("Asia/Seoul")).toString());
        assertThat(event.message()).contains("KST");
    }

    private StockCommandResultEvent sendAndReceive(StockCommandEnvelope envelope) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getCommandExchange(),
                messagingProperties.getCommandRoutingKey(),
                envelope
        );
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Object result = rabbitTemplate.receiveAndConvert(resultQueueName, 250);
            if (result instanceof StockCommandResultEvent event) {
                return event;
            }
        }
        throw new AssertionError("Timed out waiting for stock command result");
    }

    private void cacheQuote(String symbol, String price) {
        quoteRepository.save(
                new StockQuote("US", symbol, new BigDecimal(price), Instant.now()),
                Duration.ofSeconds(60)
        );
    }
}
