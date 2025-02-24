package org.team14.webty.voting.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.team14.webty.common.exception.BusinessException
import org.team14.webty.common.exception.ErrorCode
import org.team14.webty.security.authentication.AuthWebtyUserProvider
import org.team14.webty.security.authentication.WebtyUserDetails
import org.team14.webty.voting.cache.VoteCacheService
import org.team14.webty.voting.dto.VoteRequest
import org.team14.webty.voting.entity.Similar
import org.team14.webty.voting.entity.Vote
import org.team14.webty.voting.enums.VoteType
import org.team14.webty.voting.mapper.VoteMapper.toEntity
import org.team14.webty.voting.repository.SimilarRepository
import org.team14.webty.voting.repository.VoteRepository
import java.util.function.Supplier

@Service
class VoteService(
    private val voteRepository: VoteRepository,
    private val similarRepository: SimilarRepository,
    private val voteCacheService: VoteCacheService,
    private val authWebtyUserProvider: AuthWebtyUserProvider
) {
    // 유사 투표
    @Transactional
    fun vote(webtyUserDetails: WebtyUserDetails, voteRequest: VoteRequest): Long {
        val webtyUser = authWebtyUserProvider.getAuthenticatedWebtyUser(webtyUserDetails)
        val similar = similarRepository.findById(voteRequest.similarId)
            .orElseThrow { BusinessException(ErrorCode.SIMILAR_NOT_FOUND) }
        // 중복 투표 방지
        if (voteRepository.existsByUserIdAndSimilar(webtyUser.userId!!, similar)) {
            throw BusinessException(ErrorCode.VOTE_ALREADY_EXISTS)
        }
        val vote = toEntity(webtyUser, similar, voteRequest.voteType)
        voteRepository.save(vote)

        // Redis에 투표 수 반영
        voteCacheService.incrementVote(similar.similarId!!, VoteType.fromString(voteRequest.voteType))

        updateSimilarResult(similar)
        return vote.voteId!!
    }

    // 투표 취소
    @Transactional
    fun cancel(webtyUserDetails: WebtyUserDetails, voteId: Long) {
        authWebtyUserProvider.getAuthenticatedWebtyUser(webtyUserDetails)
        val vote: Vote = voteRepository.findById(voteId)
            .orElseThrow(Supplier { BusinessException(ErrorCode.VOTE_NOT_FOUND) })
        voteRepository.delete(vote)

        // Redis에서 투표 수 감소
        voteCacheService.decrementVote(vote.similar.similarId!!, vote.voteType)
        
        updateSimilarResult(vote.similar)
    }

    private fun updateSimilarResult(existingSimilar: Similar) {
        val similarId = existingSimilar.similarId!!

        // Redis에서 동의 및 비동의 투표 수 가져오기
        val agreeCount = voteCacheService.getVoteCount(similarId, VoteType.AGREE)
        val disagreeCount = voteCacheService.getVoteCount(similarId, VoteType.DISAGREE)

        // similarResult 업데이트
        val updatedSimilarResult = agreeCount - disagreeCount

        // 기존 값과 비교하여 변경된 경우만 DB 업데이트
        if (existingSimilar.similarResult != updatedSimilarResult) {
            val updatedSimilar = existingSimilar.copy(similarResult = updatedSimilarResult)
            similarRepository.save(updatedSimilar)
        }
    }
}
