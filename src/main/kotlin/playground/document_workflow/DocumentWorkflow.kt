package playground.document_workflow

sealed interface DocumentState
object Draft : DocumentState
object Reviewed : DocumentState
object Published : DocumentState
object Archived : DocumentState

data class Document<S: DocumentState>(val id: String, val content: String)

fun reviewDocument(doc: Document<Draft>): Document<Reviewed> {
    println("Reviewing document: ${doc.id}")
    return Document(doc.id, doc.content)
}

fun publishDocument(doc: Document<Reviewed>): Document<Published> {
    println("Publishing document: ${doc.id}")
    return Document(doc.id, doc.content)
}

fun archiveDocument(doc: Document<Published>): Document<Archived> {
    println("Archiving document: ${doc.id}")
    return Document(doc.id, doc.content)
}

fun <S: DocumentState> deleteDocument(doc: Document<S>): String {
    println("Deleting document: ${doc.id}")
    return "Document ${doc.id} deleted."
}

fun main() {
    val draft = Document<Draft>("doc-123", "This is a draft.")
    val reviewed = reviewDocument(draft)
    val published = publishDocument(reviewed)
    val archived = archiveDocument(published)

    val deleteMsg = deleteDocument(archived)
}