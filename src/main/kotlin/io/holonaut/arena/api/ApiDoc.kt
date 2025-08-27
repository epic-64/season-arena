package io.holonaut.arena.api

import io.ktor.http.*
import kotlinx.serialization.Serializable

object ApiRegistry {
    private val _routes = mutableListOf<RouteInfo>()
    val routes: List<RouteInfo> get() = _routes

    fun register(method: HttpMethod, path: String, description: String? = null, sampleBody: String? = null) {
        _routes.removeAll { it.method == method.value && it.path == path }
        _routes += RouteInfo(method.value, path, description, sampleBody)
    }
}

@Serializable
data class RouteInfo(
    val method: String,
    val path: String,
    val description: String? = null,
    val sampleBody: String? = null
)