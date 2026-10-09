// Recommender2.kt: building blocks for an on-device, account-free recommender.
// Sketch written against your description of Recommender.kt, not against your real
// types. Adapt the data classes and persistence to your project. Not compiled here.

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

private const val DAY_MS = 86_400_000.0

/* ============================================================
 * 1. Richer implicit feedback and time-decayed affinity
 * ============================================================ */

enum class Origin { USER_CHOSE, AUTO_QUEUE, MIX }   // who started the play

data class PlayEvent(
    val trackId: String,
    val artistId: String,
    val timestampMs: Long,
    val listenedMs: Long,
    val durationMs: Long,
    val origin: Origin,
    val restarted: Boolean = false,
)

/** One play becomes one reward in [-1, 1.5]. Skips only hurt when WE suggested the track. */
fun eventReward(e: PlayEvent): Double {
    val completion = (e.listenedMs.toDouble() / max(1L, e.durationMs)).coerceIn(0.0, 1.0)
    val suggested = e.origin != Origin.USER_CHOSE
    return when {
        e.restarted -> 1.5                                   // replay is a strong positive
        completion >= 0.8 -> 1.0                             // played through
        e.listenedMs < 30_000 -> if (suggested) -1.0 else -0.3   // early skip
        completion < 0.5 -> if (suggested) -0.4 else -0.1    // mid-track bail
        else -> 0.2                                          // mostly listened
    }
}

/** Sum of rewards, each decayed by its own age. Replaces "freshness from last play only". */
fun decayedRewards(events: List<PlayEvent>, now: Long, halfLifeDays: Double): Map<String, Pair<Double, Double>> {
    val lambda = ln(2.0) / (halfLifeDays * DAY_MS)
    val pos = HashMap<String, Double>()
    val neg = HashMap<String, Double>()
    for (e in events) {
        val w = exp(-lambda * (now - e.timestampMs))
        val r = eventReward(e) * w
        if (r >= 0) pos.merge(e.trackId, r, Double::plus) else neg.merge(e.trackId, -r, Double::plus)
    }
    return (pos.keys + neg.keys).associateWith { (pos[it] ?: 0.0) to (neg[it] ?: 0.0) }
}

/** Long-term affinity (90 d half-life) or short-term (5 d). Skips saturate and fade. */
fun affinity(posNeg: Pair<Double, Double>, liked: Boolean): Double =
    (if (liked) 3.0 else 0.0) + 1.5 * ln(1 + posNeg.first) - 2.0 * ln(1 + posNeg.second)

/** Confidence in the profile, 0..1. Use it to blend personal signal with generic signal. */
fun profileConfidence(totalEvents: Int) = 1 - exp(-totalEvents / 20.0)

/* ============================================================
 * 2. Local song graph (persist this in SQLite) and Personalized PageRank
 * ============================================================ */

class SongGraph {
    private val edges = HashMap<String, HashMap<String, Double>>()

    /** Call every time you fetch YouTube's "up next" list for [source]. Keep edges for weeks, not 20 min. */
    fun addList(source: String, ranked: List<String>, k: Int = 10) {
        val out = edges.getOrPut(source) { HashMap() }
        ranked.forEachIndexed { rank, id ->
            if (id != source) out.merge(id, 1.0 / (k + rank + 1), Double::plus)
        }
    }

    /** Random walk with restart: r = a*s + (1-a) * P^T r. Reaches 2-3 hops from your seeds. */
    fun personalizedPageRank(
        seeds: Map<String, Double>, alpha: Double = 0.15, iterations: Int = 15,
    ): Map<String, Double> {
        val total = seeds.values.sum().takeIf { it > 0 } ?: return emptyMap()
        val restart = seeds.mapValues { it.value / total }
        var r: Map<String, Double> = restart
        repeat(iterations) {
            val next = HashMap<String, Double>()
            for ((s, w) in restart) next.merge(s, alpha * w, Double::plus)
            for ((node, mass) in r) {
                val out = edges[node] ?: continue
                val sum = out.values.sum()
                for ((to, ew) in out) next.merge(to, (1 - alpha) * mass * ew / sum, Double::plus)
            }
            r = next
        }
        return r.filterKeys { it !in seeds }
    }

    fun neighbours(id: String): Set<String> = edges[id]?.keys ?: emptySet()
}

fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val inter = a.count { it in b }
    return inter.toDouble() / (a.size + b.size - inter)
}

/* ============================================================
 * 3. Rank fusion (replaces weight * (1 - position/length))
 * ============================================================ */

data class RadioList(val sourceId: String, val weight: Double, val ranked: List<String>)

/** Reciprocal Rank Fusion. Independent of list length, normalised to roughly 0..1. */
fun fuse(lists: List<RadioList>, k: Int = 10): Map<String, Double> {
    val score = HashMap<String, Double>()
    for (l in lists) l.ranked.forEachIndexed { rank, id -> score.merge(id, l.weight / (k + rank + 1), Double::plus) }
    val maxPossible = lists.sumOf { it.weight } / (k + 1)
    return if (maxPossible > 0) score.mapValues { it.value / maxPossible } else score
}

/* ============================================================
 * 4. Taste seeds with diversity (MMR) instead of "top 6, max 2 per artist"
 * ============================================================ */

data class Scored(val trackId: String, val artistId: String, val score: Double)

fun pickSeeds(
    pool: List<Scored>, k: Int, lambda: Double = 0.7,
    similarity: (Scored, Scored) -> Double,
): List<Scored> {
    val chosen = ArrayList<Scored>()
    val rest = pool.sortedByDescending { it.score }.toMutableList()
    val top = rest.firstOrNull()?.score?.takeIf { it > 0 } ?: 1.0
    while (chosen.size < k && rest.isNotEmpty()) {
        val best = rest.maxByOrNull { c ->
            val redundancy = chosen.maxOfOrNull { similarity(c, it) } ?: 0.0
            lambda * (c.score / top) - (1 - lambda) * redundancy
        }!!
        chosen += best; rest -= best
    }
    return chosen
}

/* ============================================================
 * 5. Thompson sampling over candidate "lanes" (explore / exploit)
 * ============================================================ */

class LaneBandit(private val rng: java.util.Random = java.util.Random()) {
    private val a = HashMap<String, Double>()   // successes + 1
    private val b = HashMap<String, Double>()   // failures + 1

    /** success = listened >= 30 s, failure = skipped before 30 s. */
    fun record(lane: String, success: Boolean) {
        if (success) a.merge(lane, 1.0, Double::plus) else b.merge(lane, 1.0, Double::plus)
    }

    /** Call once a day: pull counts back toward the prior so old behaviour fades. */
    fun decay(factor: Double = 0.97) {
        for (k in a.keys.toList()) a[k] = 1 + (a[k]!! - 1) * factor
        for (k in b.keys.toList()) b[k] = 1 + (b[k]!! - 1) * factor
    }

    fun pick(lanes: Collection<String>): String =
        lanes.maxByOrNull { sampleBeta(a[it] ?: 1.0, b[it] ?: 1.0) }!!

    private fun sampleBeta(alpha: Double, beta: Double): Double {
        val x = sampleGamma(alpha); val y = sampleGamma(beta)
        return x / (x + y)
    }

    // Marsaglia-Tsang; shapes are always >= 1 because of the +1 prior.
    private fun sampleGamma(shape: Double): Double {
        val d = shape - 1.0 / 3; val c = 1 / sqrt(9 * d)
        while (true) {
            val x = rng.nextGaussian(); val v = (1 + c * x).let { it * it * it }
            if (v <= 0) continue
            val u = rng.nextDouble()
            if (ln(u) < 0.5 * x * x + d - d * v + d * ln(v)) return d * v
        }
    }
}

/* ============================================================
 * 6. Learned scorer: logistic regression updated online from your own skips
 * ============================================================ */

class OnlineRanker(private val prior: DoubleArray, private val priorBias: Double = -0.5) {
    private val w = prior.copyOf()
    private var bias = priorBias

    fun predict(x: DoubleArray): Double = 1 / (1 + exp(-(bias + w.indices.sumOf { w[it] * x[it] })))

    /** y = 1 if the suggested track was listened to (>= 30 s), 0 if skipped early. */
    fun update(x: DoubleArray, y: Int, lr: Double = 0.05, l2: Double = 0.01) {
        val err = predict(x) - y
        for (i in w.indices) w[i] -= lr * (err * x[i] + l2 * (w[i] - prior[i]))   // L2 toward the prior
        bias -= lr * (err + l2 * (bias - priorBias))
    }
}

/** Suggested feature order for the ranker. All values should be scaled to ~0..1. */
object Features {
    const val FUSED_RADIO = 0       // fuse() output
    const val GRAPH_PPR = 1         // personalizedPageRank score, rank-normalised
    const val SESSION_SIM = 2       // similarity to the last few completed tracks
    const val ARTIST_AFFINITY = 3   // decayed, shrunk artist affinity
    const val TRACK_AFFINITY = 4    // affinity() squashed with tanh
    const val LIKED = 5
    const val FAMILIARITY = 6       // plays / (plays + 5)
    const val REPLAY_PENALTY = 7    // exp(-hoursSinceLastPlay / 24)  (smooth, not a flat -0.8)
    const val SKIP_POSTERIOR = 8    // (skips + 1) / (impressions + 4), decayed
    const val LANGUAGE_MATCH = 9    // script/language of title vs your language mix
    const val ARTIST_RUN = 10       // how many of the last 5 queued tracks share the artist
    const val COUNT = 11

    val defaultWeights = doubleArrayOf(2.0, 1.5, 1.2, 0.8, 0.6, 0.5, 0.2, -1.5, -1.8, 0.6, -0.7)
}

/* ============================================================
 * 7. Re-ranking: relevance vs redundancy, plus a hard artist cap
 * ============================================================ */

data class Cand(val trackId: String, val artistId: String, val p: Double)

fun rerank(
    cands: List<Cand>, n: Int, lambda: Double = 0.75,
    similarity: (Cand, Cand) -> Double,
): List<Cand> {
    val out = ArrayList<Cand>()
    val rest = cands.sortedByDescending { it.p }.toMutableList()
    while (out.size < n && rest.isNotEmpty()) {
        val eligible = rest.filter { c ->
            // never 3 in a row from one artist
            !(out.size >= 2 && out[out.size - 1].artistId == c.artistId && out[out.size - 2].artistId == c.artistId)
        }.ifEmpty { rest }
        val best = eligible.maxByOrNull { c ->
            val prev = out.takeLast(3)
            val redundancy = prev.maxOfOrNull { similarity(c, it) } ?: 0.0
            lambda * c.p - (1 - lambda) * redundancy
        }!!
        out += best; rest -= best
    }
    return out
}

/* ============================================================
 * 8. Session reaction: three early skips in a row means change direction
 * ============================================================ */

class SessionState {
    private var consecutiveEarlySkips = 0
    fun onOutcome(earlySkip: Boolean): Boolean {      // returns true when you should re-seed
        consecutiveEarlySkips = if (earlySkip) consecutiveEarlySkips + 1 else 0
        return consecutiveEarlySkips >= 3
    }
    // On true: drop the lane that produced those tracks, re-seed from the last COMPLETED track
    // or a different taste cluster, bump exploration for the next 5 slots, then reset the counter.
}