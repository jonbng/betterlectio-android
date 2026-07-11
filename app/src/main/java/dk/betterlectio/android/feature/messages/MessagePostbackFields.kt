package dk.betterlectio.android.feature.messages

/**
 * Known Lectio ASP.NET field-name variants for message actions.
 * Prefer iOS/Flutter contracts; keep legacy names as fallbacks for SmartPostback.
 *
 * List actions (iOS LectioHTTPClient+Messages / Flutter controller):
 * - `__EVENTTARGET=__Page`
 * - `__EVENTARGUMENT=READMESSAGE_|FLAGMESSAGE_|HIDEMESSAGE_<normalizedId>`
 * - `ListGridSelectionTree$folders=<folderId>`
 *
 * Reply (Flutter/iOS):
 * - EditModeHeaderTitleTB / EditModeContentBBTB + MessagesGV$ctlNN$SendMessageBtn
 */
object MessagePostbackFields {
    data class ReplyTargets(
        val contentField: String,
        val sendTarget: String,
        val titleField: String = "s\$m\$Content\$Content\$MessageThreadCtrl\$EditModeHeaderTitleTB\$tb",
    )

    data class ComposeTargets(
        val recipientField: String,
        val subjectField: String,
        val bodyField: String,
        val sendTarget: String,
    )

    /** iOS/Flutter EditMode reply fields first; legacy CreateNewAnswer last. */
    val replyVariants: List<ReplyTargets> = listOf(
        ReplyTargets(
            contentField = "s\$m\$Content\$Content\$MessageThreadCtrl\$MessagesGV\$ctl02\$EditModeContentBBTB\$TbxNAME\$tb",
            sendTarget = "s\$m\$Content\$Content\$MessageThreadCtrl\$MessagesGV\$ctl02\$SendMessageBtn",
            titleField = "s\$m\$Content\$Content\$MessageThreadCtrl\$MessagesGV\$ctl02\$EditModeHeaderTitleTB\$tb",
        ),
        ReplyTargets(
            contentField = "s\$m\$Content\$Content\$MessageThreadCtrl\$EditModeContentBBTB\$TbxNAME\$tb",
            sendTarget = "s\$m\$Content\$Content\$MessageThreadCtrl\$SendMessageBtn",
            titleField = "s\$m\$Content\$Content\$MessageThreadCtrl\$EditModeHeaderTitleTB\$tb",
        ),
        ReplyTargets(
            contentField = "s\$m\$Content\$Content\$MessageThreadCtrl\$CreateNewAnswer\$WriteContent",
            sendTarget = "s\$m\$Content\$Content\$MessageThreadCtrl\$CreateNewAnswer\$SendAnswerBtn",
        ),
        ReplyTargets(
            contentField = "s\$m\$Content\$Content\$CreateNewAnswer\$WriteContent",
            sendTarget = "s\$m\$Content\$Content\$CreateNewAnswer\$SendAnswerBtn",
        ),
    )

    val composeVariants: List<ComposeTargets> = listOf(
        // Flutter multi-step compose field names (MessagesGV ctl02)
        ComposeTargets(
            recipientField = "s\$m\$Content\$Content\$MessageThreadCtrl\$addRecipientDD\$inp",
            subjectField = "s\$m\$Content\$Content\$MessageThreadCtrl\$MessagesGV\$ctl02\$EditModeHeaderTitleTB\$tb",
            bodyField = "s\$m\$Content\$Content\$MessageThreadCtrl\$MessagesGV\$ctl02\$EditModeContentBBTB\$TbxNAME\$tb",
            sendTarget = "s\$m\$Content\$Content\$MessageThreadCtrl\$MessagesGV\$ctl02\$SendMessageBtn",
        ),
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

    /** Prefer page event args (iOS/Flutter); button names are SmartPostback fallbacks only. */
    fun readMessageArg(normalizedId: String) = "READMESSAGE_$normalizedId"
    fun flagMessageArg(normalizedId: String) = "FLAGMESSAGE_$normalizedId"
    fun hideMessageArg(normalizedId: String) = "HIDEMESSAGE_$normalizedId"

    const val PAGE_EVENT_TARGET = "__Page"
    const val FOLDERS_FIELD = "s\$m\$Content\$Content\$ListGridSelectionTree\$folders"

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
