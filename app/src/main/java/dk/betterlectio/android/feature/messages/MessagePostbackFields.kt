package dk.betterlectio.android.feature.messages

/**
 * Known Lectio ASP.NET field-name variants for message actions.
 * [MessageRepository] tries these in order for live postbacks.
 */
object MessagePostbackFields {
    data class ReplyTargets(
        val contentField: String,
        val sendTarget: String,
    )

    data class ComposeTargets(
        val recipientField: String,
        val subjectField: String,
        val bodyField: String,
        val sendTarget: String,
    )

    val replyVariants: List<ReplyTargets> = listOf(
        ReplyTargets(
            contentField = "s\$m\$Content\$Content\$MessageThreadCtrl\$CreateNewAnswer\$WriteContent",
            sendTarget = "s\$m\$Content\$Content\$MessageThreadCtrl\$CreateNewAnswer\$SendAnswerBtn",
        ),
        ReplyTargets(
            contentField = "s\$m\$Content\$Content\$CreateNewAnswer\$WriteContent",
            sendTarget = "s\$m\$Content\$Content\$CreateNewAnswer\$SendAnswerBtn",
        ),
        ReplyTargets(
            contentField = "m\$Content\$Content\$MessageThreadCtrl\$CreateNewAnswer\$WriteContent",
            sendTarget = "m\$Content\$Content\$MessageThreadCtrl\$CreateNewAnswer\$SendAnswerBtn",
        ),
    )

    val composeVariants: List<ComposeTargets> = listOf(
        ComposeTargets(
            recipientField = "s\$m\$Content\$Content\$createNewMessage\$addRecipientDD\$addRecipientDD",
            subjectField = "s\$m\$Content\$Content\$createNewMessage\$MessagesSubjectBox",
            bodyField = "s\$m\$Content\$Content\$createNewMessage\$CreateNewMessage\$WriteContent",
            sendTarget = "s\$m\$Content\$Content\$createNewMessage\$CreateNewMessage\$CreateMessageButton",
        ),
        ComposeTargets(
            recipientField = "s\$m\$Content\$Content\$addRecipientDD\$addRecipientDD",
            subjectField = "s\$m\$Content\$Content\$MessagesSubjectBox",
            bodyField = "s\$m\$Content\$Content\$CreateNewMessage\$WriteContent",
            sendTarget = "s\$m\$Content\$Content\$CreateNewMessage\$CreateMessageButton",
        ),
    )

    val markReadTargets = listOf(
        "s\$m\$Content\$Content\$MarkAsReadBtn",
        "s\$m\$Content\$Content\$MarkReadBtn",
        "m\$Content\$Content\$MarkAsReadBtn",
    )

    val flagTargets = listOf(
        "s\$m\$Content\$Content\$FlagBtn",
        "s\$m\$Content\$Content\$StarBtn",
        "m\$Content\$Content\$FlagBtn",
    )

    val deleteTargets = listOf(
        "s\$m\$Content\$Content\$DeleteBtn",
        "s\$m\$Content\$Content\$DeleteThreadBtn",
        "m\$Content\$Content\$DeleteBtn",
    )
}
