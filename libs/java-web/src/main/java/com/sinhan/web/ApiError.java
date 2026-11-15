package com.sinhan.web;

public record ApiError(String code, String message, String traceId) {}
