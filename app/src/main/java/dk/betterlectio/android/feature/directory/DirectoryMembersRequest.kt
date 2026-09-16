package dk.betterlectio.android.feature.directory

/** Builds the Lectio member-list URL for each directory entity type. */
internal object DirectoryMembersRequest {
    fun path(entity: DirectoryEntity): String {
        val numericId = DirectoryParser.numericId(entity.id)
        return when (entity.kind) {
            DirectoryEntityKind.CLASS ->
                "subnav/members.aspx?klasseid=$numericId&showstudents=1&reporttype=withpics"
            DirectoryEntityKind.HOLD, DirectoryEntityKind.GROUP ->
                "subnav/members.aspx?holdelementid=$numericId&showteachers=1&showstudents=1&reporttype=withpics"
            else -> "FindSkemaBew.aspx?type=elev&nosubnav=1&relatedto=$numericId"
        }
    }
}
