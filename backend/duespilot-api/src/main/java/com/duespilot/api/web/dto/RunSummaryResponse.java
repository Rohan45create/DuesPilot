package com.duespilot.api.web.dto;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.LedgerEntryEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record RunSummaryResponse(
    AnalysisRunEntity run,
    @Nullable ReconciliationResultEntity reconciliation,
    List<LedgerEntryEntity> ledgerEntries,
    List<ConflictEntity> conflicts,
    List<EligibilityAssessmentEntity> gates,
    @Nullable InterestEstimateEntity interest,
    List<ProposedActionEntity> proposedActions
) {}
