package com.mkwang.backend.modules.accounting.service;
import java.time.LocalDate;
/** Scoped to one synchronous, authorized historical posting; never propagates to async work. */
public final class AccountingPostingContext implements AutoCloseable {
    public record Snapshot(LocalDate postingDate,LocalDate businessDate,String evidenceRef) {}
    private static final ThreadLocal<Snapshot> CURRENT=new ThreadLocal<>();
    private final Snapshot previous;
    private AccountingPostingContext(Snapshot value){previous=CURRENT.get();CURRENT.set(value);}
    public static AccountingPostingContext open(LocalDate posting,LocalDate business,String evidence){return new AccountingPostingContext(new Snapshot(posting,business,evidence));}
    public static Snapshot current(){return CURRENT.get();}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
