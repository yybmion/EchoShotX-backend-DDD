package com.example.echoshotx.credit.application.service;

import com.example.echoshotx.credit.domain.entity.CreditHistory;
import com.example.echoshotx.credit.infrastructure.persistence.CreditHistoryRepository;
import com.example.echoshotx.member.application.adaptor.MemberAdaptor;
import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.video.domain.entity.ProcessingType;
import com.example.echoshotx.video.domain.entity.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CreditService {

    private final MemberAdaptor memberAdaptor;
    private final CreditHistoryRepository creditHistoryRepository;

    /**
     * 크레딧 사용 (영상 처리용)
     */
    public CreditHistory useCreditsForVideoProcessing(Video video, ProcessingType processingType) {
        Long memberId = video.getMemberId();
        Member member = memberAdaptor.queryById(memberId);

        // 필요 크레딧 계산
        int requiredCredits = calculateRequiredCredits(processingType, video.getOriginalMetadata().getDurationSeconds());

        // 회원 크레딧 차감
        member.useCredits(requiredCredits);

        // 사용 내역 기록
        CreditHistory creditHistory = CreditHistory.createUsage(memberId, video.getId(), requiredCredits, processingType);
        return creditHistoryRepository.save(creditHistory);
    }

    private int calculateRequiredCredits(ProcessingType processingType, Double videoDurationSeconds) {
        double costPerSecond = processingType.getCreditCostPerSecond();
        double totalCost = costPerSecond * videoDurationSeconds;
        //        return Math.max(calculatedCredits, 10); // 최소 크레딧 10개 보장할 경우
        return (int) Math.ceil(totalCost);
    }

    /**
     * 크레딧 충전
     */
    public CreditHistory addCredits(Long memberId, Integer amount, String description) {
        Member member = memberAdaptor.queryById(memberId);

        // 회원 크레딧 충전
        member.addCredits(amount);

        // 충전 내역 기록
        CreditHistory creditHistory = CreditHistory.createCharge(memberId, amount, description);
        return creditHistoryRepository.save(creditHistory);
    }
    
    /**
     * 크레딧 충전 (결제 연동용)
     */
    public CreditHistory addCreditsFromPayment(Long memberId, Integer amount, String paymentId) {
        String description = String.format("크레딧 구매 (결제ID: %s)", paymentId);
        return addCredits(memberId, amount, description);
    }
    
    /**
     * 크레딧 환불
     */
    public CreditHistory refundCredits(Long memberId, Long videoId, Integer amount, String reason) {
        Member member = memberAdaptor.queryById(memberId);

        // 회원 크레딧 환불
        member.addCredits(amount);

        // 환불 내역 기록
        CreditHistory creditHistory = CreditHistory.createRefund(memberId, videoId, amount, reason);
        return creditHistoryRepository.save(creditHistory);
    }

    /**
     * AI 서버 연동용 크레딧 차감
     */
    public CreditHistory deductCredits(Long memberId, Integer amount, String description) {
        Member member = memberAdaptor.queryById(memberId);
        
        // 회원 크레딧 차감
        member.useCredits(amount);
        
        // 차감 내역 기록
        CreditHistory creditHistory = CreditHistory.createUsage(memberId, null, amount, description);
        return creditHistoryRepository.save(creditHistory);
    }

    /**
     * AI 서버 연동용 크레딧 환불
     */
    public CreditHistory refundCredits(Long memberId, Integer amount, String reason) {
        Member member = memberAdaptor.queryById(memberId);

        // 회원 크레딧 환불
        member.addCredits(amount);

        // 환불 내역 기록
        CreditHistory creditHistory = CreditHistory.createRefund(memberId, null, amount, reason);
        return creditHistoryRepository.save(creditHistory);
    }

}
