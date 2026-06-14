package forpdateam.ru.forpda.entity.remote.editpost

/**
 * Created by radiationx on 28.07.17.
 * Converted to Kotlin.
 */
class EditPoll {
    var title: String = ""
    var maxQuestions: Int = 0
    var maxChoices: Int = 0
    var baseIndexOffset: Int = 0
    var indexOffset: Int = 0
    val questions: MutableList<Question> = mutableListOf()

    fun getQuestion(index: Int): Question? = questions.getOrNull(index)

    fun addQuestion(question: Question) {
        questions.add(question)
    }

    fun increaseIndexOffset() {
        indexOffset++
    }

    fun reduceIndexOffset() {
        indexOffset--
    }

    data class Question(
        var title: String = "",
        var isMulti: Boolean = false,
        var index: Int = 0,
        var baseIndexOffset: Int = 0,
        var indexOffset: Int = 0,
        val choices: MutableList<Choice> = mutableListOf()
    ) {
        fun getChoice(index: Int): Choice? = choices.getOrNull(index)

        fun addChoice(choice: Choice) {
            choices.add(choice)
        }

        fun increaseIndexOffset() {
            indexOffset++
        }

        fun reduceIndexOffset() {
            indexOffset--
        }
    }

    data class Choice(
        var title: String = "",
        var votes: Int = 0,
        var index: Int = 0
    )

    companion object {
        fun findQuestionByIndex(poll: EditPoll, index: Int): Question? {
            return poll.questions.find { it.index == index }
        }

        fun findChoiceByIndex(question: Question, index: Int): Choice? {
            return question.choices.find { it.index == index }
        }
    }
}
