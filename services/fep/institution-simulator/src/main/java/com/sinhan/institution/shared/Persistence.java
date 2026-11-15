package com.sinhan.institution.shared;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기관별 DB 연결을 만든다. 기관마다 별도 DB 계정과 스키마를 쓰고, 그 계정에는 다른 스키마 권한이 없다(N-01).
 *
 * <p>설정 키: institution.{name}.url / username / password. 마이그레이션은 classpath:db/{name}.
 */
public final class Persistence {
  private Persistence() {}

  public record Context(DataSource dataSource, TransactionTemplate tx) {}

  public static Context open(Environment env, String name) {
    var ds = new HikariDataSource();
    ds.setPoolName(name);
    ds.setJdbcUrl(env.getRequiredProperty("institution." + name + ".url"));
    ds.setUsername(env.getRequiredProperty("institution." + name + ".username"));
    ds.setPassword(env.getRequiredProperty("institution." + name + ".password"));
    ds.setMaximumPoolSize(3);
    ds.setMinimumIdle(1);
    Flyway.configure()
        .dataSource(ds)
        .schemas(env.getRequiredProperty("institution." + name + ".schema"))
        .locations("classpath:db/" + name)
        .load()
        .migrate();
    return new Context(ds, new TransactionTemplate(new DataSourceTransactionManager(ds)));
  }
}
