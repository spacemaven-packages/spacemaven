package net.derfruhling.spacemaven.modules

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.ResourceCodeResolver
import io.ktor.server.application.*
import io.ktor.server.jte.*

fun Application.configureTemplating() {
    install(Jte) {
        if(this@configureTemplating.developmentMode) {
            val resolver = ResourceCodeResolver("templates", ClassLoader.getSystemClassLoader())
            templateEngine = TemplateEngine.create(resolver, ContentType.Html)
        } else {
            templateEngine = TemplateEngine.createPrecompiled(ContentType.Html)
        }
    }
}
