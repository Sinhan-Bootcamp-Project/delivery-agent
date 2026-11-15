package com.sinhan.institution.shared;

/**
 * 시연·시험 전용 설정. ALLOW_EARLY_CLOSE=true면 23:30 전에도 당일 영업일을 마감할 수 있다. 마감 후 같은 영업일 거래는 차액에
 * 반영되지 않으므로 운영 설정에서는 끈다.
 */
public final class DemoMode {
  private DemoMode() {}

  public static boolean earlyCloseAllowed() {
    return "true".equals(System.getenv("ALLOW_EARLY_CLOSE"));
  }
}
