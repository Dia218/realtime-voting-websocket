package org.team14.webty.voting.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.team14.webty.common.exception.BusinessException
import org.team14.webty.common.exception.ErrorCode
import org.team14.webty.security.authentication.AuthWebtyUserProvider
import org.team14.webty.security.authentication.WebtyUserDetails
import org.team14.webty.voting.cache.VoteCacheService
import org.team14.webty.voting.dto.SimilarResponse
import org.team14.webty.voting.entity.Similar
import org.team14.webty.voting.mapper.SimilarMapper.toEntity
import org.team14.webty.voting.mapper.SimilarMapper.toResponse
import org.team14.webty.voting.repository.SimilarRepository
import org.team14.webty.voting.repository.VoteRepository
import org.team14.webty.webtoon.service.WebtoonService


@Service
class SimilarService(
    private val similarRepository: SimilarRepository,
    private val webtoonService: WebtoonService,
    private val voteRepository: VoteRepository,
    private val voteCacheService: VoteCacheService,
    private val authWebtyUserProvider: AuthWebtyUserProvider
) {
    // 유사 웹툰 등록
    @Transactional
    fun createSimilar(
        webtyUserDetails: WebtyUserDetails, targetWebtoonId: Long,
        choiceWebtoonId: Long
    ): SimilarResponse {
        val webtyUser = authWebtyUserProvider.getAuthenticatedWebtyUser(webtyUserDetails)
        val targetWebtoon = webtoonService.findWebtoon(targetWebtoonId)
        val choiceWebtoon = webtoonService.findWebtoon(choiceWebtoonId)

        // 이미 등록된 유사 웹툰인지 확인
        if (similarRepository.existsByTargetWebtoonAndSimilarWebtoonId(targetWebtoon, choiceWebtoon.webtoonId!!)) {
            throw BusinessException(ErrorCode.SIMILAR_DUPLICATION_ERROR)
        }

        val similar = toEntity(
            webtyUser.userId!!,
            choiceWebtoon.webtoonId ?: throw BusinessException(ErrorCode.WEBTOON_NOT_FOUND),
            targetWebtoon
        )

        runCatching {
            similarRepository.save(similar)
        }.onFailure { e ->
            throw BusinessException(ErrorCode.SIMILAR_DUPLICATION_ERROR)
        }

        return toResponse(similar, choiceWebtoon)
    }

    // 유사 웹툰 삭제
    @Transactional
    fun deleteSimilar(webtyUserDetails: WebtyUserDetails, similarId: Long) {
        val webtyUser = authWebtyUserProvider.getAuthenticatedWebtyUser(webtyUserDetails)
        val similar = similarRepository.findByUserIdAndSimilarId(
            webtyUser.userId!!,
            similarId
        ).orElseThrow { BusinessException(ErrorCode.SIMILAR_NOT_FOUND) }

        voteRepository.deleteAll(voteRepository.findAllBySimilar(similar)) // 연관된 투표내역 삭제
        voteCacheService.deleteVotesForSimilar(similar.similarId!!) // redis 캐시 삭제

        similarRepository.delete(similar)
    }

    // 유사 리스트 By Webtoon
    @Transactional(readOnly = true)
    fun findAll(targetWebtoonId: Long, page: Int, size: Int): Page<SimilarResponse> {
        val pageable: Pageable = PageRequest.of(page, size)
        val targetWebtoon = webtoonService.findWebtoon(targetWebtoonId)
        val similars = similarRepository.findAllByTargetWebtoon(targetWebtoon, pageable)

        return similars.map { similar: Similar? ->
            toResponse(
                similar!!, webtoonService.findWebtoon(
                    similar.similarWebtoonId
                )
            )
        }
    }
}
