package com.sinhan.fep;

import com.sinhan.web.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** FEP망(fep, mock 두 네트워크에 연결). 한끼 Main망 서비스는 기관에 직접 닿지 않고 반드시 여기를 거친다. */
@SpringBootApplication
@Import({SecurityConfiguration.class, ErrorHandler.class, TraceFilter.class, ServiceHttp.class})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
