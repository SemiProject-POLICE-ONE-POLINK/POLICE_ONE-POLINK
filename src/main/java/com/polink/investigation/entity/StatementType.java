package com.polink.investigation.entity;

// 진술 유형
public enum StatementType {

    INITIAL,     // 최초 진술(추가 진술 시에는 미표기)
    ADDITIONAL,  // 추가 진술
    CORRECTION,  // 정정 진술
    REVERSAL     // 번복 진술
}
