package com.privacyguard.pro.utils

import java.util.Random

/**
 * 伪造设备标识缓存
 *
 * 设计目的：
 *  - 每个 APP 进程独立维护一份伪造的设备标识
 *  - 单次进程生命周期内保持稳定（同一 APP 重复调用 getDeviceId 总返回相同伪造值）
 *  - 进程重启后重新随机（避免长期固定被关联）
 *
 * 注意：Root 版的系统级 Hook（SystemPropSpoofHook）会通过 setprop
 * 在更底层修改属性，与本缓存的 Java 层值保持一致以避免检测。
 */
object FakeDeviceCache {

    private val random = Random(System.currentTimeMillis())

    // 进程级缓存的伪造值（懒加载，第一次访问时生成）
    val fakeImei: String by lazy { randomImei() }
    val fakeMeid: String by lazy { randomMeid() }
    val fakeSubscriberId: String by lazy { randomNumeric(15) }
    val fakeSimSerial: String by lazy { randomNumeric(20) }
    val fakeLine1Number: String by lazy { randomPhoneNumber() }
    val fakeAndroidId: String by lazy { randomHex(16) }
    val fakeMacAddress: String by lazy { randomMac() }
    val fakeBssid: String by lazy { randomMac() }
    val fakeSerial: String by lazy { randomAlphaNumeric(11) }
    val fakeAdvertisingId: String by lazy { randomUuid() }

    private fun randomImei(): String = randomNumeric(15)

    private fun randomMeid(): String = randomHex(14)

    private fun randomNumeric(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(random.nextInt(10)) }
        return sb.toString()
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(length)
        repeat(length) { sb.append(chars[random.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun randomAlphaNumeric(length: Int): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val sb = StringBuilder(length)
        repeat(length) { sb.append(chars[random.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun randomMac(): String {
        val sb = StringBuilder()
        for (i in 0 until 6) {
            if (i > 0) sb.append(":")
            sb.append(String.format("%02X", random.nextInt(256)))
        }
        return sb.toString()
    }

    private fun randomPhoneNumber(): String {
        val prefixes = listOf("138", "139", "186", "187", "159", "152", "176", "177")
        val prefix = prefixes[random.nextInt(prefixes.size)]
        val sb = StringBuilder(prefix)
        repeat(8) { sb.append(random.nextInt(10)) }
        return sb.toString()
    }

    private fun randomUuid(): String {
        return String.format("%s-%s-%s-%s-%s",
            randomHex(8), randomHex(4), randomHex(4), randomHex(4), randomHex(12))
    }
}
