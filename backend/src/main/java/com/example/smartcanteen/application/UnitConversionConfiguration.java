package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.UnitConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnitConversionConfiguration {

    @Bean
    public UnitConverter unitConverter() {
        return new UnitConverter();
    }
}
