package com.polink.casefile.service;

import com.polink.casefile.dto.ai.SimilarCaseAiInput;
import com.polink.casefile.dto.ai.SimilarCaseAiResult;

public interface SimilarCaseAiClient {

    SimilarCaseAiResult analyze(SimilarCaseAiInput input);
}