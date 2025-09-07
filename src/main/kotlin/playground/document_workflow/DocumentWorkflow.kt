package playground.document_workflow

sealed interface DocumentState
object Draft : DocumentState
object Reviewed : DocumentState
object Published : DocumentState
object Archived : DocumentState

class Document<S: DocumentState> private constructor(val id: String, val content: String) {
    companion object {
        fun draft(id: String, content: String): Document<Draft> {
            println("Creating draft document: $id")
            return Document(id, content)
        }

        fun review(doc: Document<Draft>): Document<Reviewed> {
            println("Reviewing document: ${doc.id}")
            return Document(doc.id, doc.content)
        }

        fun publish(doc: Document<Reviewed>): Document<Published> {
            println("Publishing document: ${doc.id}")
            return Document(doc.id, doc.content)
        }

        fun archive(doc: Document<Published>): Document<Archived> {
            println("Archiving document: ${doc.id}")
            return Document(doc.id, doc.content)
        }

        fun <S: DocumentState> delete(doc: Document<S>): String {
            println("Deleting document: ${doc.id}")
            return "Document ${doc.id} deleted."
        }
    }
}

fun main() {
    val draft = Document.draft("doc-123", "This is a draft.")
    val reviewed = Document.review(draft)
    val published = Document.publish(reviewed)
    val archived = Document.archive(published)
    val deletionMessage = Document.delete(archived)
}