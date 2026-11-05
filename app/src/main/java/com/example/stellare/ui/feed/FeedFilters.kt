package com.example.stellare.ui.feed

import com.example.stellare.data.model.PostModel

data class FeedFilters(
    val query: String = "",
    val tags: Set<String> = emptySet()
) {
    fun matchesPost(post: PostModel): Boolean {
        val pasujeTekst = query.isBlank() ||
            post.title.contains(query, ignoreCase = true) ||
            post.body.contains(query, ignoreCase = true)
        val pasujeTagi = tags.isEmpty() || post.tags.any { tags.contains(it) }
        return pasujeTekst && pasujeTagi
    }
}
