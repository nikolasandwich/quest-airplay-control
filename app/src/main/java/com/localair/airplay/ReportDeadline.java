package com.localair.airplay;

/** A sent report has exactly one terminal outcome; expiration requires retiring its transport. */
public final class ReportDeadline {
    private final long expiresAt;
    private boolean finished;
    public ReportDeadline(long sentAt){expiresAt=sentAt+1000;}
    public boolean acknowledge(){if(finished)return false;finished=true;return true;}
    public boolean expire(long now){if(finished||now<expiresAt)return false;finished=true;return true;}
}
