package net.derfruhling.spacemaven

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.StringValue
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class PublishPrincipal(val username: String, val authority: List<String>)

fun Application.configureSecurity() {
    install(Authentication) {
        basic("publish") {
            realm = "spacemaven.publishing"
            validate { credentials ->
                val digest = MessageDigest.getInstance("SHA-256");
                val bytes = digest.digest(credentials.password.toByteArray())

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