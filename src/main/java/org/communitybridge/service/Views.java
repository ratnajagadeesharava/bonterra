package org.communitybridge.service;

import java.util.List;
import org.communitybridge.db.FundRepository.FundFigures;
import org.communitybridge.db.Times;
import org.communitybridge.domain.AssistanceRequest;
import org.communitybridge.domain.Fund;
import org.communitybridge.domain.Money;
import org.communitybridge.domain.Participant;
import org.communitybridge.domain.Payment;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.FundingTotals;
import org.communitybridge.service.result.ParticipantView;
import org.communitybridge.service.result.PaymentView;
import org.communitybridge.service.result.RequestView;

/** Domain records to output views. Every cents value gets its formatted twin; nothing here does business logic. */
final class Views {

    private Views() {
    }

    static long available(Fund f) {
        return Math.subtractExact(Math.subtractExact(f.allocationCents(), f.committedCents()), f.paidCents());
    }

    static RequestView request(AssistanceRequest r) {
        return new RequestView(r.id(), r.participantId(), r.fundId(), r.category(), r.purpose(),
                r.amountCents(), Money.format(r.amountCents()), Money.CURRENCY, r.status(), r.clientRequestId(),
                r.createdBy(), Times.format(r.createdAt()), r.decidedBy(), Times.format(r.decidedAt()), r.rejectionReason());
    }

    static ParticipantView participant(Participant p) {
        return new ParticipantView(p.id(), p.name(), p.householdSize(), p.city());
    }

    static PaymentView payment(Payment p) {
        return new PaymentView(p.id(), p.requestId(), p.amountCents(), Money.format(p.amountCents()), Money.CURRENCY,
                p.recordedBy(), Times.format(p.recordedAt()));
    }

    static FundSummary fund(FundFigures figures) {
        Fund f = figures.fund();
        long available = available(f);
        return new FundSummary(f.id(), f.name(), f.status(), Money.CURRENCY,
                f.allocationCents(), f.committedCents(), f.paidCents(), available,
                figures.pendingCents(), figures.pendingCount(),
                Money.format(f.allocationCents()), Money.format(f.committedCents()), Money.format(f.paidCents()),
                Money.format(available), Money.format(figures.pendingCents()));
    }

    /** Sums over the given funds, closed ones included. */
    static FundingTotals totals(List<FundSummary> funds) {
        long allocation = 0;
        long committed = 0;
        long paid = 0;
        long available = 0;
        long pending = 0;
        for (FundSummary f : funds) {
            allocation = Math.addExact(allocation, f.allocationCents());
            committed = Math.addExact(committed, f.committedCents());
            paid = Math.addExact(paid, f.paidCents());
            available = Math.addExact(available, f.availableCents());
            pending = Math.addExact(pending, f.pendingCents());
        }
        return new FundingTotals(Money.CURRENCY, allocation, committed, paid, available, pending,
                Money.format(allocation), Money.format(committed), Money.format(paid), Money.format(available),
                Money.format(pending));
    }
}
