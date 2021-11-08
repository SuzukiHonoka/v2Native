package org.starx_software_lab.v2native.util

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.starx_software_lab.v2native.ui.home.HomeFragment
import org.starx_software_lab.v2native.util.CIDR.Companion.preferredDns
import java.io.File

class Config {
    companion object {
        var configPath = ""

        fun updateConfigPath(v: Context): Boolean {
            configPath = v.filesDir.absolutePath + "/config.json"
            return true
        }

        private fun writeConfig(config: String) =
            File(configPath).writeText(config)

        fun checkConfig() =
            File(configPath).exists()

        fun getServerIP(): String? {
            val obj = JsonParser.parseString(File(configPath).readText()).asJsonObject
            val server = getServerBlock(obj)
            if (server != null) {
                val serverIP =
                    server.asJsonObject.get("settings").asJsonObject.get("vnext").asJsonArray.get(0).asJsonObject.get(
                        "address"
                    ).asString
                Log.d(HomeFragment.TAG, "parser: address -> $serverIP")
                return serverIP
            }
            return null
        }

        private fun getServerBlock(json: JsonObject): JsonElement? {
            json.getAsJsonArray("outbounds").forEach { els ->
                if (els.asJsonObject.get("tag").asString == "proxy") {
//                    val serverIP =
//                        els.asJsonObject.get("settings").asJsonObject.get("vnext")
//                            .asJsonArray.get(0).asJsonObject.get("address").asString
//                    els.asJsonObject.get("settings").asJsonObject.get("vnext")
//                        .asJsonArray.get(0).asJsonObject.remove("address")
//                    val address = InetAddress.getByName(serverIP).hostAddress
//                    els.asJsonObject.get("settings").asJsonObject.get("vnext")
//                        .asJsonArray.get(0).asJsonObject.addProperty("address",address)
//                    Log.d(TAG, "getServerBlock: $serverIP -> $address")
                    Log.d(HomeFragment.TAG, "parser: proxy outbound found!")
                    return els
                }
            }
            return null
        }

        fun reWriteConfig(v: Context, s: String): Boolean {
            // clean up
            val obj = JsonParser.parseString(s).asJsonObject.apply {
                remove("inbounds")
                remove("dns")
                remove("policy")
                remove("routing")
            }.also { els ->
                // get first proxy outbound
                getServerBlock(els).also { el ->
                    if (el == null) {
                        return false
                    }
                    // remove original outbounds in case having many
                    els.remove("outbounds")
                    // add to outbounds list: note that only one outbound will work
                    els.add("outbounds", JsonArray().apply {
                        add(el)
                        // dns outbound
                        add(JsonObject().apply {
                            addProperty("tag", "dns-out")
                            addProperty("protocol", "dns")
                        })
                        // direct outbound
                        add(JsonObject().apply {
                            addProperty("tag", "direct")
                            addProperty("protocol", "freedom")
                        })
                    })
                }
            }
            // apply sniffing by default in case any kind of dns pollution (hijacks)
            val sniffing = JsonObject().apply {
                val protocols = JsonArray().apply {
                    // include http/s
                    add("tls")
                    add("http")
                }
                add("destOverride", protocols)
                addProperty("enabled", true)
            }
            // expose socks outbound
            val socks = JsonObject().apply {
                addProperty(
                    "listen",
                    // if need to be public
                    if (Utils.getPerfBool(v, "export", false)) "0.0.0.0" else "127.0.0.1"
                )
                addProperty("port", 10808)
                addProperty("protocol", "socks")
                addProperty("tag", "socks")
                add("sniffing", sniffing)
            }
            // iptables interacting outbound
            val tproxy = JsonObject().apply {
                addProperty("port", 12345)
                addProperty("protocol", "dokodemo-door")
                addProperty("tag", "tproxy")
                add("sniffing", sniffing)
                add("settings", JsonObject().apply {
                    addProperty("network", "tcp,udp")
                    addProperty("followRedirect", true)
                })
                add("streamSettings", JsonObject().apply {
                    add("sockopt", JsonObject().apply {
                        if (Utils.isTproxyEnabled(v)) {
                            // use tproxy if enabled else redirect by default
                            addProperty("tproxy", "tproxy")
                        }
                    })
                })
            }

            val dnsOut = JsonObject().apply {
                addProperty("tag", "dns-in")
                addProperty("protocol", "dokodemo-door")
                addProperty("port", 5354) // not using 53 as default in case maya causing issue
                add("settings", JsonObject().apply {
                    addProperty("address", "127.0.0.1")
//                    addProperty("port",53)
                    addProperty("network", "tcp,udp")
                })
            }

            val dns = JsonObject().apply {
                val servers = JsonArray().apply {
                    // add dns server
                    preferredDns.forEach { s ->
                        add(JsonObject().apply {
                            addProperty("address", s)
                            add("domains", JsonArray().apply {
                                add("geolocation-!cn")
                            })
                            // addProperty("expectIPs","!geosite:cn")
                        })
                    }
                    add(JsonObject().apply {
                        addProperty("address", "119.29.29.29")
                        add("domains", JsonArray().apply {
                            add("geosite:cn")
                        })
                    })
                }
                add("servers", servers)
            }
            val routing = JsonObject().apply {
                addProperty("domainStrategy", "IPIfNonMatch")
                addProperty("domainMatcher", "mph")
                val rules = JsonArray().apply {
                    val directSites = JsonObject().apply {
                        add("domains", JsonArray().apply {
                            add("geosite:cn")
                        })
                        addProperty("outboundTag", "direct")
                        addProperty("type", "field")
                    }
                    val directIPs = JsonObject().apply {
                        add("ip", JsonArray().apply {
                            add("119.29.29.29")
                            add("geoip:private")
                            add("geoip:cn")
                        })
                        addProperty("outboundTag", "direct")
                        addProperty("type", "field")
                    }
                    val redirectDns = JsonObject().apply {
                        addProperty("inboundTag", "dns-in")
                        addProperty("outboundTag", "dns-out")
                        addProperty("type", "field")
                    }
                    val proxyDNS = JsonObject().apply {
                        val dnsIP = JsonArray().apply {
                            // use proxy to query dns
                            preferredDns.forEach { s ->
                                add(s)
                            }
                        }
                        add("ip", dnsIP)
                        addProperty("outboundTag", "proxy")
                        addProperty("port", 53)
                        addProperty("type", "field")
                    }
                    add(directSites)
                    add(directIPs)
                    add(redirectDns)
                    add(proxyDNS)
                }
                add("rules", rules)
            }
            obj.add("dns", dns)
            obj.add("routing", routing)
            obj.add("inbounds", JsonArray().apply {
                add(dnsOut)
                add(socks)
                add(tproxy)
            })
            Log.d(HomeFragment.TAG, "formattedJson: $obj")
            writeConfig(obj.toString())
            return true
        }
    }
}