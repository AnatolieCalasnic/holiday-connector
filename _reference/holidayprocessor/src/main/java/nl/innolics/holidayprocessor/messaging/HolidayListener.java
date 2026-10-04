package nl.innolics.holidayprocessor.messaging;

import java.nio.charset.StandardCharsets;

import nl.innolics.holidayprocessor.config.ProcessorProperties;
import nl.innolics.holidayprocessor.store.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * One message at a time: decode, validate, store.
 * Invalid messages go to the DLQ at once with the reason in a header; they are never retried.
 * Anything thrown from here (database down) is retried by the container, then dead-lettered.
 */
@Component
public class HolidayListener {

    static final String REASON_HEADER = "x-reject-reason";

    private static final Logger log = LoggerFactory.getLogger(HolidayListener.class);

    private final MessageDecoder decoder;
    private final HolidayRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ProcessorProperties properties;

    public HolidayListener(MessageDecoder decoder, HolidayRepository repository,
                           RabbitTemplate rabbitTemplate, ProcessorProperties properties) {
        this.decoder = decoder;
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @RabbitListener(queues = "${processor.queue}")
    public void onMessage(Message message) {
        HolidayRecord record;
        try {
            record = decoder.decode(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (InvalidMessageException e) {
            message.getMessageProperties().setHeader(REASON_HEADER, e.getMessage());
            rabbitTemplate.send("", properties.deadLetterQueue(), message);
            log.warn("Dead-lettered: {}", e.getMessage());
            return;
        }

        HolidayRepository.Outcome outcome = repository.apply(record);
        log.info("{} {}", outcome, record.id());
    }
}
