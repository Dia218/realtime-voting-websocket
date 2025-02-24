package org.team14.webty.voting.cache

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.team14.webty.voting.enums.VoteType

@Service
class VoteCacheService(private val redisTemplate: RedisTemplate<String, String>) {

    private fun getKey(similarId: Long, voteType: VoteType): String {
        return "vote:$similarId:$voteType"
    }

    fun incrementVote(similarId: Long, voteType: VoteType) {
        val key = getKey(similarId, voteType)
        redisTemplate.opsForValue().increment(key)
    }

    fun decrementVote(similarId: Long, voteType: VoteType) {
        val key = getKey(similarId, voteType)
        redisTemplate.opsForValue().decrement(key)
    }

    fun getVoteCount(similarId: Long, voteType: VoteType): Long {
        val key = getKey(similarId, voteType)
        return redisTemplate.opsForValue().get(key)?.toLong() ?: 0
    }

    fun setVoteCount(similarId: Long, voteType: VoteType, count: Long) {
        val key = getKey(similarId, voteType)
        redisTemplate.opsForValue().set(key, count.toString())
    }

    fun deleteVotesForSimilar(similarId: Long) {
        VoteType.entries.forEach { voteType ->
            val key = getKey(similarId, voteType)
            redisTemplate.delete(key)
        }
    }
}
