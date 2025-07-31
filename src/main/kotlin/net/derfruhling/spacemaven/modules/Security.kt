package net.derfruhling.spacemaven.modules

import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.StringValue
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import java.security.MessageDigest

data class PublishPrincipal(val username: String, val authority: List<String>)

fun Application.configureSecurity() {
    install(Authentication) {
        basic("publish") {
            realm = "spacemaven.publishing"
            validate { credentials ->
                val digest = MessageDigest.getInstance("SHA-256");
                val bytes = digest.digest(credentials.password.toByteArray())

                val datastore by inject<Datastore>()

                val key = datastore.newKeyFactory()
                    .setKind("PublishAuthority")
                    .newKey(credentials.name)

                val authority = withContext(Dispatchers.IO) { datastore.get(key) }

                if(authority != null && authority.getString("key").decodeBase64Bytes().contentEquals(bytes)) {
                    PublishPrincipal(credentials.name, authority.getList<StringValue>("authority").map { it.get() })
                } else {
                    null
                }
            }
        }
    }
}