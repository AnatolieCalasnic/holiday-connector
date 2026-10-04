package nl.innolics.holidayprocessor.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The processor declares and binds its own queue. The queue returned by registration never
 * receives data (ADR-001, finding 1). Start this service before the adapter: a message published
 * before the binding exists is dropped by RabbitMQ without an error.
 */
@Configuration
public class RabbitConfig {

    /** Same declaration as sazdatastream's (durable topic), so redeclaring it is harmless. */
    @Bean
    TopicExchange dataExchange(ProcessorProperties props) {
        return new TopicExchange(props.exchange());
    }

    @Bean
    Queue holidayQueue(ProcessorProperties props) {
        return QueueBuilder.durable(props.queue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.deadLetterQueue())
                .build();
    }

    @Bean
    Queue holidayDeadLetterQueue(ProcessorProperties props) {
        return QueueBuilder.durable(props.deadLetterQueue()).build();
    }

    @Bean
    Binding holidayBinding(Queue holidayQueue, TopicExchange dataExchange, ProcessorProperties props) {
        return BindingBuilder.bind(holidayQueue).to(dataExchange).with(props.routingKey());
    }

    /**
     * After the last retry, park the message in the DLQ. Spring adds x-exception-message and
     * x-exception-stacktrace headers, so the reason travels with it.
     */
    @Bean
    MessageRecoverer deadLetterRecoverer(RabbitTemplate rabbitTemplate, ProcessorProperties props) {
        return new RepublishMessageRecoverer(rabbitTemplate, "", props.deadLetterQueue());
    }
}
