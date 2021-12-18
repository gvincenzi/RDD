package org.rdc.scheduler.instruction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.java.Log;
import org.rdc.scheduler.binding.message.DistributionMessage;
import org.rdc.scheduler.domain.entity.Participant;
import org.rdc.scheduler.domain.entity.RDCItem;
import org.rdc.scheduler.notifier.valence.NotifierValenceService;
import org.rdc.scheduler.spike.client.SpikeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log
@Data
@Configuration
public class ScheduledInstruction {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SpikeClient spikeClient;

    @Autowired
    NotifierValenceService notifierValenceService;

    @Value("${rdc.instructionScheduled}")
    Boolean instructionScheduledActive;

    @Scheduled(fixedDelay = 60000)
    public void integrityVerification() {
        if(instructionScheduledActive) {
            log.info("START >> Scheduled Instruction - IntegrityVerification");
            DistributionMessage<Void> integrityVerification = spikeClient.integrityVerification();
            DistributionMessage<List<RDCItem>> integrityVerificationResponse = spikeClient.getResult(integrityVerification.getCorrelationID());
            while (integrityVerificationResponse == null || integrityVerificationResponse.getContent() == null) {
                integrityVerificationResponse = spikeClient.getResult(integrityVerification.getCorrelationID());
            }

            if (Boolean.FALSE.equals(integrityVerificationResponse.getRdcValid())) {
                log.info("ALERT >> Scheduled Instruction - IntegrityVerification : Corruption detected");
                List<RDCItem> items = objectMapper.convertValue(integrityVerificationResponse.getContent(), new TypeReference<>() {
                });
                Set<Participant> participants = new HashSet<>();
                for (RDCItem rdcItem : items) {
                    if (rdcItem.getOwner() != null) {
                        participants.add(rdcItem.getOwner());
                    }
                }
                notifierValenceService.sendCorruptionMail(participants);
            }
            log.info("END >> Scheduled Instruction - IntegrityVerification");
        }
    }
}