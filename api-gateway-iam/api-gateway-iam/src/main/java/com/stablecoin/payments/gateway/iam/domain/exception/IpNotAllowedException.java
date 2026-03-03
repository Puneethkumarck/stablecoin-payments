package com.stablecoin.payments.gateway.iam.domain.exception;

public class IpNotAllowedException extends RuntimeException {

    private IpNotAllowedException(String message) {
        super(message);
    }

    public static IpNotAllowedException of(String ip) {
        return new IpNotAllowedException("IP address not in allowlist: " + ip);
    }
}
