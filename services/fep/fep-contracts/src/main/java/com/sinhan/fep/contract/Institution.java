package com.sinhan.fep.contract;

/** 가상 금융기관 코드. 전문 공통 헤더의 기관코드와 FEP 라우팅 키로 쓴다. */
public enum Institution {
  CARD("CRD", "가상 카드사"),
  BANK("088", "가상 은행"),
  KFTC("KFT", "가상 금융결제원"),
  BOK("BOK", "가상 한국은행");

  public final String code;
  public final String label;

  Institution(String code, String label) {
    this.code = code;
    this.label = label;
  }

  public static Institution of(String code) {
    for (var i : values()) if (i.code.equals(code)) return i;
    throw new IllegalArgumentException("unknown institution " + code);
  }
}
