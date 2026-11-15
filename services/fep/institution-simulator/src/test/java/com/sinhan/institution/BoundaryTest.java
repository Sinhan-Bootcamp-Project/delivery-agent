package com.sinhan.institution;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

/** 기관끼리 코드로 직접 의존하지 않는다. 기관 간 통신은 shared.InterbankNetwork 전문으로만. */
@AnalyzeClasses(packages = "com.sinhan.institution", importOptions = ImportOption.DoNotIncludeTests.class)
class BoundaryTest {
  private static ArchRule isolated(String self, String... others) {
    String[] pkgs = new String[others.length];
    for (int i = 0; i < others.length; i++) pkgs[i] = "com.sinhan.institution." + others[i] + "..";
    return noClasses()
        .that()
        .resideInAPackage("com.sinhan.institution." + self + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(pkgs);
  }

  @ArchTest static final ArchRule card = isolated("card", "bank", "kftc", "bok");
  @ArchTest static final ArchRule bank = isolated("bank", "card", "kftc", "bok");
  @ArchTest static final ArchRule kftc = isolated("kftc", "card", "bank", "bok");
  @ArchTest static final ArchRule bok = isolated("bok", "card", "bank", "kftc");

  @ArchTest
  static final ArchRule domainIsPure =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "..infrastructure..", "..application..");
}
