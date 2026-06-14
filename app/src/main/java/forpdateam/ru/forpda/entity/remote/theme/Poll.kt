package forpdateam.ru.forpda.entity.remote.theme

import java.util.ArrayList

/**
 * Created by radiationx on 12.11.16.
 */

class Poll {
    var title: String? = null
    var votesCount: Int = 0
    var formAction: String? = null
    var formMethod: String = "get"
    val hiddenInputs = mutableListOf<Pair<String, String>>()
    var resultsUrl: String? = null
    //true - result poll
    var isResult: Boolean = false
    var voteButton = false
    var showResultsButton = false
    var showPollButton = false
    val questions = mutableListOf<PollQuestion>()

    val hasVoteInputs: Boolean
        get() = questions.any { question ->
            question.questionItems.any { item ->
                item.type.equals("radio", ignoreCase = true) ||
                        item.type.equals("checkbox", ignoreCase = true)
            }
        }

    val canVote: Boolean
        get() = !isResult && (voteButton || hasVoteInputs)

    fun haveButtons(): Boolean {
        return canVote or showResultsButton or showPollButton
    }
}
