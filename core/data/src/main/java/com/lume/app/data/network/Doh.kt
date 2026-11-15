package com.lume.app.data.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/** DNS over HTTPS providers (Settings → Network). */
enum class DohProvider(val id: String, val label: String, val url: String, val bootstrap: List<String>) {
    Cloudflare("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    Google("google", "Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    AdGuard("adguard", "AdGuard", "https://unfiltered.adguard-dns.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    Quad9("quad9", "Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112"));

    companion object {
        fun byId(id: String?): DohProvider? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The resolver every request uses. It can be switched at runtime: the system resolver by
 * default, or a DNS over HTTPS provider (useful where a provider blocks manga sites by DNS).
 */
class SwitchableDns : Dns {
    @Volatile
    private var delegate: Dns = Dns.SYSTEM

    fun use(provider: DohProvider?) {
        delegate = provider?.let(::build) ?: Dns.SYSTEM
    }

    override fun lookup(hostname: String): List<InetAddress> = delegate.lookup(hostname)

    private fun build(provider: DohProvider): Dns {
        // The DoH client itself resolves with the system DNS (its host is pinned by bootstrap IPs).
        val bootstrapClient = OkHttpClient.Builder().build()
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.url.toHttpUrl())
            .bootstrapDnsHosts(provider.bootstrap.map { InetAddress.getByName(it) })
            .build()
    }
}
