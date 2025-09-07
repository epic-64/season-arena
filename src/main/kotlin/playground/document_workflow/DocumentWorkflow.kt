package playground.document_workflow

sealed interface DocumentState
object Draft : DocumentState
object Reviewed : DocumentState
object Published : DocumentState
object Archived : DocumentState

class Document<S: DocumentState>(val id: String, val content: String) {
    companion object {
        fun draft(id: String, content: String): Document<Draft> {
            println("Creating draft document: $id")
            return Document(id, content)
        }
    }
}

fun Document<Draft>.review(): Document<Reviewed> {
    println("Reviewing document: $id")
    return Document(id, content)
}

fun Document<Reviewed>.publish(): Document<Published> {
    println("Publishing document: $id")
    return Document(id, content)
}

fun Document<Published>.archive(): Document<Archived> {
    println("Archiving document: $id")
    return Document(id, content)
}

fun <S: DocumentState> Document<S>.delete(): String {
    println("Deleting document: $id")
    return "Document $id deleted."
}

fun main() {
    val draft = Document.draft("doc-123", "This is a draft.")
    val reviewed = draft.review()
    val published = reviewed.publish()
    val archived = published.archive()
    val deletionMessage = archived.delete()
}