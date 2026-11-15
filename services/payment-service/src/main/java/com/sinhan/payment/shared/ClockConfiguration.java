package com.sinhan.payment.shared;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
class ClockConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
