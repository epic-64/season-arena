package playground.references

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class Hobby(val name: String)
data class MutablePerson(val name: String, val hobbies: MutableList<Hobby>)
data class ImmutablePerson(val name: String, val hobbies: ImmutableList<Hobby>)

fun main() {
    val alice = MutablePerson("Alice", mutableListOf(Hobby("Reading"), Hobby("Hiking")))
    println("alice's hobbies: ${alice.hobbies.map { it.name }}")

    val bob = ImmutablePerson("Bob", alice.hobbies.toImmutableList())
    println("bob's hobbies: ${bob.hobbies.map { it.name }}")

    alice.hobbies.clear()

    println("alice's hobbies: ${alice.hobbies.map { it.name }}")
    println("bob's hobbies: ${bob.hobbies.map { it.name }}")
}

