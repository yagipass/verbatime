package io.github.yagipass.verbatime.examples.batch;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.Receipt;

@Configuration
public class PlaceOrdersJobConfig {

    @Bean
    ItemReader<OrderLine> orderLineReader() {
        return new ListItemReader<>(List.of(new OrderLine("widget", 3), new OrderLine("gadget", 2), new OrderLine("gizmo", 7), new OrderLine("widget", 1), new OrderLine("doohickey", 5)));
    }

    @Bean
    ItemProcessor<OrderLine, Receipt> placeOrderProcessor(final OrderService orders) {
        return line -> orders.placeOrder(line.sku(), line.qty());
    }

    @Bean
    JdbcBatchItemWriter<Receipt> receiptWriter(final DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Receipt>().dataSource(dataSource).sql("insert into orders (sku, qty, cents, tx_id) values (:sku, :qty, :cents, :txId)").itemSqlParameterSourceProvider(receipt -> new MapSqlParameterSource().addValue("sku", receipt.sku()).addValue("qty", receipt.qty()).addValue("cents", receipt.cents()).addValue("txId", receipt.txId())).build();
    }

    @Bean
    Step placeOrdersStep(final JobRepository jobRepository, final PlatformTransactionManager tx, final ItemReader<OrderLine> reader, final ItemProcessor<OrderLine, Receipt> processor, final JdbcBatchItemWriter<Receipt> writer) {
        return new StepBuilder("placeOrders", jobRepository).<OrderLine, Receipt>chunk(2).transactionManager(tx).reader(reader).processor(processor).writer(writer).build();
    }

    @Bean
    Job placeOrdersJob(final JobRepository jobRepository, final Step placeOrdersStep) {
        return new JobBuilder("placeOrders", jobRepository).incrementer(new RunIdIncrementer()).start(placeOrdersStep).build();
    }
}
