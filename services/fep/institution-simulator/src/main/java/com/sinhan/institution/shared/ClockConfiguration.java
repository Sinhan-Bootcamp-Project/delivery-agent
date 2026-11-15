package com.sinhan.institution.shared;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
class ClockConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
