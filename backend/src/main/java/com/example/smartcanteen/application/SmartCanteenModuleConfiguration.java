package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.ProcurementService;
import com.example.smartcanteen.domain.UnitConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmartCanteenModuleConfiguration {

    @Bean
    public UnitConverter unitConverter() {
        return new UnitConverter();
    }

    @Bean
    public ProcurementService procurementService(UnitConverter unitConverter) {
        return new ProcurementService(unitConverter);
    }
}
