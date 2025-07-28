package net.derfruhling.spacemaven

import com.google.cloud.logging.LogEntry
import com.google.cloud.logging.LoggingEnhancer

class CloudEnhancer : LoggingEnhancer {
    override fun enhanceLogEntry(entry: LogEntry.Builder) {
        entry.addLabel("thread", Thread.currentThread().name)
    }
}