package com.derekwinters.intervaltrainer

/**
 * Formats a whole number of seconds as `m:ss` — minutes unpadded and unbounded, seconds always two
 * digits (BUILD-030, BUILD-031).
 *
 * The minutes field is never wrapped at an hour: a two-hour interval reads `120:00`, not `0:00`.
 *
 * The digits are built by hand rather than with `String.format`, which renders `%d` in the default
 * locale's numbering system and would emit non-ASCII digits on a device set to one of them
 * (BUILD-033).
 *
 * @throws IllegalArgumentException if [totalSeconds] is negative (BUILD-032).
 */
fun formatSeconds(totalSeconds: Int): String {
    require(totalSeconds >= 0) { "duration must not be negative, was $totalSeconds" }
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
