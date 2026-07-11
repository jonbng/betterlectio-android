package dk.betterlectio.android.feature.settings

/**
 * Canonical subject key resolution + icon name for schedule cards.
 * Expanded Danish subject set (iOS SubjectMapper parity for codes/aliases).
 * Icon names are stable string keys mapped to Compose ImageVectors in UI.
 */
object SubjectIcons {

    data class SubjectMeta(
        val canonicalKey: String,
        val defaultName: String,
        val iconKey: String,
    )

    private val byAlias: Map<String, SubjectMeta> = buildMap {
        fun add(key: String, name: String, icon: String, vararg aliases: String) {
            val meta = SubjectMeta(key, name, icon)
            put(fold(key), meta)
            aliases.forEach { put(fold(it), meta) }
        }
        add("ap", "Almen sprogforståelse", "globe", "almen sprogforstaaelse", "almen sprogforståelse")
        add("as", "Astronomi", "sparkles", "astronomi")
        add("at", "AT", "doc", "almen studieforberedelse")
        add("bi", "Biologi", "science", "bio", "biologi")
        add("bk", "Billedkunst", "brush", "billedkunst")
        add("bro", "Brobygning", "link", "brobygning")
        add("bt", "Bioteknologi", "science", "bioteknologi")
        add("da", "Dansk", "book", "dan", "dansk")
        add("de", "Design", "brush", "design")
        add("dho", "DHO", "doc")
        add("dr", "Dramatik", "theater", "dramatik")
        add("en", "Engelsk", "translate", "eng", "engelsk")
        add("er", "Erhvervsøkonomi", "chart", "erhvervsoekonomi", "erhvervsøkonomi")
        add("eø", "Erhvervsøkonomi", "chart", "eoe", "eo")
        add("ff", "Forsøgsfag", "bulb", "forsøgsfag", "forsoegsfag")
        add("fi", "Filosofi", "chat", "filosofi")
        add("fr", "Fransk", "book", "frb", "frf", "fransk")
        add("fy", "Fysik", "science", "fys", "fysik")
        add("ge", "Geografi", "globe", "geo", "geografi")
        add("hi", "Historie", "history", "his", "historie")
        add("id", "Idræt", "sport", "idræt", "idraet")
        add("if", "Idéhistorie", "history", "idehistorie", "idéhistorie")
        add("ih", "Idéhistorie", "history")
        add("it", "Informatik", "computer", "informatik")
        add("inf", "Informatik", "computer")
        add("ke", "Kemi", "science", "kem", "kemi")
        add("kit", "Kommunikation/IT", "chat", "kommunikation/it", "kommunikation it")
        add("ks", "Kultur- og samfundsfag", "building", "kultur- og samfundsfag")
        add("kt", "Klassens Time", "people", "klassens time")
        add("la", "Latin", "book", "latin")
        add("ma", "Matematik", "functions", "mat", "matematik")
        add("me", "Mediefag", "film", "mediefag")
        add("mu", "Musik", "music", "musik")
        add("ng", "Naturgeografi", "globe", "naturgeografi")
        add("nv", "Naturvidenskab", "science", "naturvidenskab", "nat")
        add("ol", "Oldtidskundskab", "building", "oldtidskundskab")
        add("pro", "Programmering", "computer", "programmering")
        add("ps", "Psykologi", "chat", "psy", "psykologi")
        add("re", "Religion", "building", "rel", "religion")
        add("sa", "Samfundsfag", "building", "samf", "samfundsfag")
        add("sp", "Spansk", "translate", "spa", "spansk")
        add("ty", "Tysk", "translate", "tys", "tysk")
        add("th", "Teknologihistorie", "history", "teknologihistorie")
        add("vk", "Virksomhedsøkonomi", "chart", "virksomhedsoekonomi")
    }

    fun fold(raw: String): String =
        raw.trim().lowercase()
            .replace("æ", "ae")
            .replace("ø", "oe")
            .replace("å", "aa")
            .replace(Regex("""\s+"""), " ")

    /**
     * Resolve icon key for a hold/title string (e.g. "1x Ma A", "Dansk", "fy").
     */
    fun iconKeyFor(title: String): String {
        val meta = resolve(title) ?: return "school"
        return meta.iconKey
    }

    fun canonicalKeyFor(title: String): String? = resolve(title)?.canonicalKey

    fun resolve(title: String): SubjectMeta? {
        val t = fold(title)
        if (t.isEmpty()) return null
        // Exact alias
        byAlias[t]?.let { return it }
        // Tokenize "1x Ma A" / "Ma A" / codes
        val tokens = t.split(Regex("""[\s\-_/]+""")).filter { it.isNotEmpty() }
        for (token in tokens) {
            byAlias[token]?.let { return it }
            // Strip trailing level letter: "maa" already folded; "ma a" handled by tokens
            if (token.length >= 2) {
                byAlias[token.take(2)]?.let { return it }
            }
        }
        // Contains full name
        for ((alias, meta) in byAlias) {
            if (alias.length >= 3 && t.contains(alias)) return meta
        }
        return null
    }
}
