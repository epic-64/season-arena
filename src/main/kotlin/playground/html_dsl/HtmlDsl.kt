package playground.html_dsl

@DslMarker
annotation class HtmlTagMarker

@HtmlTagMarker
class Html {
    private val children = mutableListOf<String>()

    fun head(block: Head.() -> Unit) {
        children += "<head>${Head().apply(block).render()}</head>"
    }

    fun body(block: Body.() -> Unit) {
        children += "<body>${Body().apply(block).render()}</body>"
    }

    fun render(): String = "<!DOCTYPE html><html>${children.joinToString("")}</html>"
}

@HtmlTagMarker
class Head {
    private val children = mutableListOf<String>()

    fun title(text: String) {
        children += "<title>$text</title>"
    }

    fun meta(block: Meta.() -> Unit) {
        children += Meta().apply(block).render()
    }

    fun render(): String = children.joinToString("")
}

enum class MetaCharset(val charset: String) {
    UTF8("UTF-8"),
    ISO88591("ISO-8859-1")
}

@HtmlTagMarker
class Meta {
    private val children = mutableListOf<String>()

    fun charset(charset: MetaCharset) {
        children += "<meta charset=\"${charset.charset}\">"
    }

    // function that sets initial-scale to 1.0
    fun mobileViewport() {
        children += """<meta name="viewport" content="width=device-width, initial-scale=1.0">"""
    }

    fun render(): String = children.joinToString("")
}

@HtmlTagMarker
class Body {
    private val children = mutableListOf<String>()

    fun p(text: String) {
        children += "<p>$text</p>"
    }

    fun render(): String = children.joinToString("")
}

fun html(block: Html.() -> Unit): String =
    Html().apply(block).render()

fun main() {
    val page = html {
        head {
            title("My page")
            meta {
                charset(MetaCharset.UTF8)
                mobileViewport()
            }
        }
        body {
            p("Hello!")
            p("Welcome to my page.")
        }
    }

    println(page)
}