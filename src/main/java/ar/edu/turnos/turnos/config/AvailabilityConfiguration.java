package ar.edu.turnos.turnos.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AvailabilityConfiguration {
    @Bean
    Clock availabilityClock(@Value("${turnos.availability.zone-id}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder availabilityRestClientBuilder() {
        return RestClient.builder();
    }
}
