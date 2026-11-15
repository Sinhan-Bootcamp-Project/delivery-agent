package com.sinhan.payment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 컨텍스트 경계. 다른 컨텍스트의 application(공개 API)과 domain 값 타입은 쓸 수 있지만 infrastructure·presentation은 쓸 수 없다.
 * 원장은 ledger.application.LedgerService로만 기장한다.
 */
@AnalyzeClasses(packages = "com.sinhan.payment", importOptions = ImportOption.DoNotIncludeTests.class)
class BoundaryTest {
  private static final String[] CONTEXTS = {"payment", "paymoney", "ledger", "settlement", "outbox"};

  @ArchTest
  static void noCrossContextInfrastructure(com.tngtech.archunit.core.domain.JavaClasses classes) {
    for (var self : CONTEXTS)
      for (var other : CONTEXTS) {
        if (self.equals(other)) continue;
        noClasses()
            .that()
            .resideInAPackage("com.sinhan.payment." + self + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.sinhan.payment." + other + ".infrastructure..",
                "com.sinhan.payment." + other + ".presentation..")
            .check(classes);
      }
  }

  @ArchTest
  static final ArchRule onlyLedgerServiceWritesLedger =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.sinhan.payment.ledger..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("com.sinhan.payment.ledger.domain.Ledger");

  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "..infrastructure..", "..application..");

  @ArchTest
  static final ArchRule onlyFepGatewayCallsFep =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .haveSimpleName("FepGateway");
}
