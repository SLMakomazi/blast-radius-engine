package com.madlanga.lab.payment.exception;

public class DownstreamException extends RuntimeException {
    private final Integer downstreamStatus;

    public DownstreamException(Integer downstreamStatus) {
        super("Downstream request failed");
        this.downstreamStatus = downstreamStatus;
    }

    public Integer downstreamStatus() { return downstreamStatus; }
}
